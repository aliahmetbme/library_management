package com.example.library.api;

import com.example.library.integration.AbstractIntegrationTest;
import com.example.library.model.*;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowRecordRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.dto.BorrowRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API (End-to-End) Tests — LibraryApiIT
 *
 * Starts the full Spring Boot application on a random port and exercises every
 * layer (Controller → Service → Repository → PostgreSQL) through real HTTP
 * requests via TestRestTemplate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibraryApiIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
        borrowRecordRepository.deleteAll();
        bookRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private Book createTestBook(String isbn, String title, String author) {
        return bookRepository.save(new Book(isbn, title, author, 3, Genre.TECHNOLOGY));
    }

    private Book createTestBook(String isbn, String title, String author, int copies, Genre genre) {
        return bookRepository.save(new Book(isbn, title, author, copies, genre));
    }

    private Member createTestMember(String name, String email, MembershipType type) {
        return memberRepository.save(new Member(name, email, type));
    }

    private long borrowAndVerify(Long bookId, Long memberId) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/borrows", new BorrowRequest(bookId, memberId), Map.class);

        assertThat(response.getStatusCode())
                .as("Pre-condition: POST /api/borrows must return 201 CREATED")
                .isEqualTo(HttpStatus.CREATED);

        return ((Number) response.getBody().get("id")).longValue();
    }

    @Nested
    @DisplayName("POST /api/books")
    class CreateBookApi {

        @Test
        @DisplayName("should create a book and return 201")
        void shouldCreateBook_WhenValidPayload() {
            // Arrange: Prepare a valid new-book request body
            Book newBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            // Act: Submit the book creation request
            ResponseEntity<Book> response = restTemplate.postForEntity(
                    baseUrl + "/books", newBook, Book.class);

            // Assert: Verify that the book is persisted and returned with a generated ID and correct properties
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Clean Code");
            assertThat(response.getBody().getAvailableCopies()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400_WhenRequiredFieldsMissing() {
            // Arrange: Build an empty book with missing fields (no ISBN, title, or author)
            Book invalidBook = new Book();

            // Act: Attempt to submit the invalid book creation request
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", invalidBook, Map.class);

            // Assert: Verify that validation rejects the request with 400 Bad Request
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when ISBN already exists")
        void shouldReturn400_WhenDuplicateIsbn() {
            // Arrange: Persist a book that occupies the ISBN we will try to reuse, and prepare a duplicate book payload
            createTestBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin");
            Book duplicate = new Book("978-0-13-468599-1", "Another Book", "Another Author", 2, Genre.FICTION);

            // Act: Attempt to create a second book with the duplicate ISBN
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", duplicate, Map.class);

            // Assert: Verify that unique ISBN constraint produces 400 Bad Request
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/books")
    class GetBooksApi {

        @Test
        @DisplayName("should return all books")
        void shouldReturnAllBooks_WhenBooksExist() {
            // Arrange: Seed the database with two distinct books
            createTestBook("978-1", "Book A", "Author A");
            createTestBook("978-2", "Book B", "Author B");

            // Act: Retrieve the full book catalogue via GET
            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books", Book[].class);

            // Assert: Verify that status is 200 OK and both seeded books appear in the response catalogue
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should return 404 for non-existent book")
        void shouldReturn404_WhenBookIdDoesNotExist() {
            // Arrange: Ensure database is clean of books and define a non-existent book ID
            long nonExistentId = 999L;

            // Act: Request the book with the non-existent ID via GET
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/books/" + nonExistentId, Map.class);

            // Assert: Verify that the API responds with 404 Not Found
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("POST /api/borrows — Happy path")
    class BorrowFlowApi {

        @Test
        @DisplayName("should borrow a book and return 201 with correct response body")
        void shouldBorrowBook_WhenAllConditionsMet() {
            // Arrange: Seed one book and one active member, and prepare a borrow request payload
            Book book = createTestBook("978-1", "Test Book", "Test Author");
            Member member = createTestMember("Alice", "alice@test.com", MembershipType.STANDARD);
            BorrowRequest request = new BorrowRequest(book.getId(), member.getId());

            // Act: Submit the borrow request via POST
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows", request, Map.class);

            // Assert: Verify that the borrow record is created with 201 Created and correct metadata in response body
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsEntry("bookTitle", "Test Book");
            assertThat(response.getBody()).containsEntry("memberName", "Alice");
            assertThat(response.getBody()).containsEntry("status", "BORROWED");
        }

        @Test
        @DisplayName("should decrease available copies by 1 after borrowing")
        void shouldDecreaseAvailableCopies_WhenBookIsBorrowed() {
            // Arrange: Seed a book with 3 copies and a member, and perform a borrow operation
            Book book = createTestBook("978-2", "Copy Count Book", "Author X");
            Member member = createTestMember("Bob", "bob@test.com", MembershipType.STANDARD);
            borrowAndVerify(book.getId(), member.getId());

            // Act: Fetch the book's current state from the API via GET
            ResponseEntity<Book> response = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);

            // Assert: Verify that the available copies have dropped from 3 to 2
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getAvailableCopies()).isEqualTo(2);
        }

        @Test
        @DisplayName("should restore available copies by 1 after returning")
        void shouldIncreaseAvailableCopies_WhenBookIsReturned() {
            // Arrange: Seed a book and a member, borrow the book, and get the borrow ID
            Book book = createTestBook("978-3", "Return Test Book", "Author Y");
            Member member = createTestMember("Carol", "carol@test.com", MembershipType.STANDARD);
            long borrowId = borrowAndVerify(book.getId(), member.getId());

            // Act: Return the borrowed book via POST return endpoint
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows/" + borrowId + "/return", null, Map.class);

            // Assert: Verify status is 200 OK, status is returned, and available copies are restored to 3
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsEntry("status", "RETURNED");

            ResponseEntity<Book> bookState = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookState.getBody().getAvailableCopies()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("POST /api/borrows — Error cases")
    class BorrowErrorsApi {

        @Test
        @DisplayName("should return 409 when member has reached borrowing limit")
        void shouldReturn409_WhenBorrowLimitExceeded() {
            // Arrange: Seed a STUDENT member (limit of 2), and seed 3 books. Borrow the first 2 books to reach limit.
            Member student = createTestMember("Student Sam", "sam@test.com", MembershipType.STUDENT);
            Book book1 = createTestBook("978-1", "Book One", "Author One");
            Book book2 = createTestBook("978-2", "Book Two", "Author Two");
            Book book3 = createTestBook("978-3", "Book Three", "Author Three");
            borrowAndVerify(book1.getId(), student.getId());
            borrowAndVerify(book2.getId(), student.getId());

            // Act: Attempt to borrow a third book which would exceed the STUDENT limit
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book3.getId(), student.getId()), Map.class);

            // Assert: Verify that the API returns 409 Conflict with a message mentioning the limit
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString()).contains("limit");
        }

        @Test
        @DisplayName("should return 409 when book has no available copies")
        void shouldReturn409_WhenNoCopiesAvailable() {
            // Arrange: Seed a book with 1 copy and two members. Borrow the book with the first member to make it unavailable.
            Book singleCopy = createTestBook("978-single", "Only One Copy", "Some Author", 1, Genre.FICTION);
            Member memberA = createTestMember("Member A", "a@test.com", MembershipType.STANDARD);
            Member memberB = createTestMember("Member B", "b@test.com", MembershipType.STANDARD);
            borrowAndVerify(singleCopy.getId(), memberA.getId());

            // Act: Attempt to borrow the now-unavailable book with the second member
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(singleCopy.getId(), memberB.getId()), Map.class);

            // Assert: Verify that the API returns 409 Conflict and a message indicating no available copies
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString()).contains("No available copies");
        }

        @Test
        @DisplayName("should return 404 when member does not exist")
        void shouldReturn404_WhenMemberNotFound() {
            // Arrange: Seed a book and prepare a non-existent member ID
            Book book = createTestBook("978-404m", "Existing Book", "Author");
            long nonExistentMemberId = 999_999L;

            // Act: Attempt to borrow using the non-existent member ID
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book.getId(), nonExistentMemberId), Map.class);

            // Assert: Verify that the API returns 404 Not Found and a message indicating member is missing
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString()).contains("Member not found");
        }

        @Test
        @DisplayName("should return 404 when book does not exist")
        void shouldReturn404_WhenBookNotFound() {
            // Arrange: Seed a member and prepare a non-existent book ID
            Member member = createTestMember("Active Member", "active@test.com", MembershipType.STANDARD);
            long nonExistentBookId = 999_999L;

            // Act: Attempt to borrow a book that does not exist
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(nonExistentBookId, member.getId()), Map.class);

            // Assert: Verify that the API returns 404 Not Found with a book not found message
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString()).contains("Book not found");
        }
    }

    @Nested
    @DisplayName("Member API")
    class MemberApiTests {

        @Test
        @DisplayName("should create a member and return 201")
        void shouldCreateMember_WhenValidPayload() {
            // Arrange: Build a valid member request payload
            Member newMember = new Member("Jane Doe", "jane.doe@test.com", MembershipType.PREMIUM);

            // Act: Submit the member creation request via POST
            ResponseEntity<Member> response = restTemplate.postForEntity(
                    baseUrl + "/members", newMember, Member.class);

            // Assert: Verify member is created with 201 Created and correct active status and details
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Jane Doe");
            assertThat(response.getBody().getEmail()).isEqualTo("jane.doe@test.com");
            assertThat(response.getBody().getMembershipType()).isEqualTo(MembershipType.PREMIUM);
            assertThat(response.getBody().isActive()).isTrue();
        }

        @Test
        @DisplayName("should deactivate a member via DELETE")
        void shouldDeactivateMember_WhenDeleteIsCalled() {
            // Arrange: Persist an active member who will be soft-deleted
            Member member = createTestMember("To Be Deactivated", "deact@test.com", MembershipType.STANDARD);

            // Act: Call DELETE on the member's resource URI and retrieve the deactivated member's state via GET
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    baseUrl + "/members/" + member.getId(),
                    HttpMethod.DELETE, null, Void.class);
            ResponseEntity<Member> getResponse = restTemplate.getForEntity(
                    baseUrl + "/members/" + member.getId(), Member.class);

            // Assert: Verify DELETE returns 204 No Content and the GET request shows active as false
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().isActive()).isFalse();
        }

        @Test
        @DisplayName("should return 400 when creating member with invalid email")
        void shouldReturn400_WhenInvalidEmail() {
            // Arrange: Build a member payload with an invalid email address format
            Member invalid = new Member("Bad Email User", "not-a-valid-email", MembershipType.STANDARD);

            // Act: Submit the member creation request with the malformed email
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/members", invalid, Map.class);

            // Assert: Verify that validation rejects with 400 Bad Request and details of the email validation error
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("errors");

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldErrors = (Map<String, Object>) response.getBody().get("errors");
            assertThat(fieldErrors).containsKey("email");
        }
    }

    @Nested
    @DisplayName("Search & Filter API")
    class SearchApiTests {

        @Test
        @DisplayName("should return only matching books via GET /api/books/search?keyword=")
        void shouldSearchBooks_WhenKeywordMatchesTitles() {
            // Arrange: Seed three books, two matching the clean keyword and one that does not
            createTestBook("978-s1", "Clean Code", "Robert C. Martin");
            createTestBook("978-s2", "Clean Architecture", "Robert C. Martin");
            createTestBook("978-s3", "The Pragmatic Programmer", "Andrew Hunt");

            // Act: Search with the keyword "clean" via GET books search API
            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books/search?keyword=clean", Book[].class);

            // Assert: Verify that only the two matching books are returned in any order
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody())
                    .extracting(Book::getTitle)
                    .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
        }

        @Test
        @DisplayName("should return only active borrows for a specific member")
        void shouldGetActiveBorrows_WhenMemberHasReturnedOneBook() {
            // Arrange: Seed a member, borrow two books, and return the first book to leave one active borrow
            Member member = createTestMember("Reader", "reader@test.com", MembershipType.STANDARD);
            Book book1 = createTestBook("978-ab1", "Book One", "Author");
            Book book2 = createTestBook("978-ab2", "Book Two", "Author");

            long borrow1Id = borrowAndVerify(book1.getId(), member.getId());
            borrowAndVerify(book2.getId(), member.getId());

            restTemplate.postForEntity(
                    baseUrl + "/borrows/" + borrow1Id + "/return", null, Map.class);

            // Act: Request only the active borrowing records for this member via GET
            ResponseEntity<Map[]> response = restTemplate.getForEntity(
                    baseUrl + "/borrows/member/" + member.getId() + "/active", Map[].class);

            // Assert: Verify that only one active record (Book Two in status BORROWED) is returned
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody()[0]).containsEntry("status", "BORROWED");
            assertThat(response.getBody()[0]).containsEntry("bookTitle", "Book Two");
        }
    }
}
