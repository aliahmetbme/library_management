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

//API TEST

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

    // Helper methods

    private Book createTestBook(String isbn, String title, String author) {
        Book book = new Book(isbn, title, author, 3, Genre.TECHNOLOGY);
        return bookRepository.save(book);
    }

    private Member createTestMember(String name, String email, MembershipType type) {
        Member member = new Member(name, email, type);
        return memberRepository.save(member);
    }
    // EXAMPLE: Book API tests — filled in

    @Nested
    @DisplayName("POST /api/books")
    class CreateBookApi {

        @Test
        @DisplayName("should create a book and return 201")
        void shouldCreateBook() {
            Book newBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            ResponseEntity<Book> response = restTemplate.postForEntity(
                    baseUrl + "/books", newBook, Book.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Clean Code");
            assertThat(response.getBody().getAvailableCopies()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400_WhenFieldsMissing() {
            Book invalidBook = new Book(); // no required fields set

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", invalidBook, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when duplicate ISBN")
        void shouldReturn400_WhenDuplicateIsbn() {
            createTestBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin");

            Book duplicate = new Book("978-0-13-468599-1", "Another Book", "Another Author", 2, Genre.FICTION);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", duplicate, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/books")
    class GetBooksApi {

        @Test
        @DisplayName("should return all books")
        void shouldReturnAllBooks() {
            createTestBook("978-1", "Book A", "Author A");
            createTestBook("978-2", "Book B", "Author B");

            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books", Book[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should return 404 for non-existent book")
        void shouldReturn404_WhenBookNotFound() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/books/999", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // EXAMPLE: Borrow flow

    @Nested
    @DisplayName("Borrow Flow (POST /api/borrows)")
    class BorrowFlowApi {

        @Test
        @DisplayName("should complete full borrow-return cycle")
        void shouldCompleteBorrowReturnCycle() {
            // Setup
            Book book = createTestBook("978-1", "Test Book", "Test Author");
            Member member = createTestMember("Alice", "alice@test.com", MembershipType.STANDARD);

            // 1. Borrow the book
            BorrowRequest borrowRequest = new BorrowRequest(book.getId(), member.getId());
            ResponseEntity<Map> borrowResponse = restTemplate.postForEntity(
                    baseUrl + "/borrows", borrowRequest, Map.class);

            assertThat(borrowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(borrowResponse.getBody()).containsEntry("bookTitle", "Test Book");
            assertThat(borrowResponse.getBody()).containsEntry("memberName", "Alice");
            assertThat(borrowResponse.getBody()).containsEntry("status", "BORROWED");

            Number borrowId = (Number) borrowResponse.getBody().get("id");

            // 2. Verify book availability decreased
            ResponseEntity<Book> bookResponse = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookResponse.getBody().getAvailableCopies()).isEqualTo(2);

            // 3. Return the book
            ResponseEntity<Map> returnResponse = restTemplate.postForEntity(
                    baseUrl + "/borrows/" + borrowId.longValue() + "/return",
                    null, Map.class);

            assertThat(returnResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(returnResponse.getBody()).containsEntry("status", "RETURNED");

            // 4. Verify book availability increased back
            bookResponse = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookResponse.getBody().getAvailableCopies()).isEqualTo(3);
        }
    }
    // Implemented API tests (error cases, member CRUD, search & filtering)

    @Nested
    @DisplayName("POST /api/borrows - Error cases")
    class BorrowErrorsApi {

        @Test
        @DisplayName("should return 409 when borrowing limit exceeded")
        void shouldReturn409_WhenBorrowLimitExceeded() {
            // A STUDENT member is allowed to borrow at most 2 books.
            Member student = createTestMember("Student Sam", "sam@test.com", MembershipType.STUDENT);
            Book book1 = createTestBook("978-1", "Book One", "Author One");
            Book book2 = createTestBook("978-2", "Book Two", "Author Two");
            Book book3 = createTestBook("978-3", "Book Three", "Author Three");

            // Borrow the first two books
            ResponseEntity<Map> first = restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(book1.getId(), student.getId()), Map.class);
            ResponseEntity<Map> second = restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(book2.getId(), student.getId()), Map.class);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // The third borrow exceeds the STUDENT limit and must be rejected.
            ResponseEntity<Map> third = restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(book3.getId(), student.getId()), Map.class);

            assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(third.getBody()).isNotNull();
            assertThat(third.getBody().get("message").toString()).contains("limit");
        }

        @Test
        @DisplayName("should return 409 when no copies available")
        void shouldReturn409_WhenNoCopiesAvailable() {
            // A book that has only a single copy in the library.
            Book singleCopy = bookRepository.save(
                    new Book("978-single", "Only One Copy", "Some Author", 1, Genre.FICTION));
            Member memberA = createTestMember("Member A", "a@test.com", MembershipType.STANDARD);
            Member memberB = createTestMember("Member B", "b@test.com", MembershipType.STANDARD);

            // The first member takes the only copy — succeeds and drops availability to 0.
            ResponseEntity<Map> firstBorrow = restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(singleCopy.getId(), memberA.getId()), Map.class);
            assertThat(firstBorrow.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // The second member tries to borrow the now-unavailable book.
            ResponseEntity<Map> secondBorrow = restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(singleCopy.getId(), memberB.getId()), Map.class);

            assertThat(secondBorrow.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(secondBorrow.getBody()).isNotNull();
            assertThat(secondBorrow.getBody().get("message").toString()).contains("No available copies");
        }

        @Test
        @DisplayName("should return 404 when member does not exist")
        void shouldReturn404_WhenMemberNotFound() {
            // A real book exists, but the member id does not. The service
            // validates the member first, so a 404 for the member is expected.
            Book book = createTestBook("978-404m", "Existing Book", "Author");
            long nonExistentMemberId = 999_999L;

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book.getId(), nonExistentMemberId), Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("message").toString()).contains("Member not found");
        }

        @Test
        @DisplayName("should return 404 when book does not exist")
        void shouldReturn404_WhenBookNotFound() {
            // The member is valid and active; only the book id is missing.
            // (An inactive member would fail earlier with a 400, so we use an active one.)
            Member member = createTestMember("Active Member", "active@test.com", MembershipType.STANDARD);
            long nonExistentBookId = 999_999L;

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(nonExistentBookId, member.getId()), Map.class);

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
        void shouldCreateMember() {
            Member newMember = new Member("Jane Doe", "jane.doe@test.com", MembershipType.PREMIUM);

            ResponseEntity<Member> response = restTemplate.postForEntity(
                    baseUrl + "/members", newMember, Member.class);

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
        void shouldDeactivateMember() {
            Member member = createTestMember("To Be Deactivated", "deact@test.com", MembershipType.STANDARD);
            assertThat(member.isActive()).isTrue();

            // DELETE deactivates the member (soft delete) and returns 204 No Content.
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    baseUrl + "/members/" + member.getId(),
                    HttpMethod.DELETE, null, Void.class);
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Fetching the member again must now show active = false.
            ResponseEntity<Member> getResponse = restTemplate.getForEntity(
                    baseUrl + "/members/" + member.getId(), Member.class);
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().isActive()).isFalse();
        }

        @Test
        @DisplayName("should return 400 when creating member with invalid email")
        void shouldReturn400_WhenInvalidEmail() {
            // Name is valid; the email is malformed, which must fail @Email validation.
            Member invalid = new Member("Bad Email User", "not-a-valid-email", MembershipType.STANDARD);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/members", invalid, Map.class);

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
        @DisplayName("should search books by keyword via GET /api/books/search?keyword=...")
        void shouldSearchBooks() {
            createTestBook("978-s1", "Clean Code", "Robert C. Martin");
            createTestBook("978-s2", "Clean Architecture", "Robert C. Martin");
            createTestBook("978-s3", "The Pragmatic Programmer", "Andrew Hunt");

            // The keyword matches the title of the two "Clean" books (case-insensitive).
            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books/search?keyword=clean", Book[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody())
                    .extracting(Book::getTitle)
                    .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
        }

        @Test
        @DisplayName("should get active borrows for a member")
        void shouldGetActiveBorrows() {
            Member member = createTestMember("Reader", "reader@test.com", MembershipType.STANDARD);
            Book book1 = createTestBook("978-ab1", "Book One", "Author");
            Book book2 = createTestBook("978-ab2", "Book Two", "Author");

            // Borrow both books.
            ResponseEntity<Map> borrow1 = restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(book1.getId(), member.getId()), Map.class);
            restTemplate.postForEntity(
                    baseUrl + "/borrows", new BorrowRequest(book2.getId(), member.getId()), Map.class);
            assertThat(borrow1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Return the first book.
            Number borrowId = (Number) borrow1.getBody().get("id");
            restTemplate.postForEntity(
                    baseUrl + "/borrows/" + borrowId.longValue() + "/return", null, Map.class);

            // Only one active (still-borrowed) record should remain — the second book.
            ResponseEntity<Map[]> response = restTemplate.getForEntity(
                    baseUrl + "/borrows/member/" + member.getId() + "/active", Map[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody()[0]).containsEntry("status", "BORROWED");
            assertThat(response.getBody()[0]).containsEntry("bookTitle", "Book Two");
        }
    }
}
