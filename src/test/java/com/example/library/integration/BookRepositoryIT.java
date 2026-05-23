package com.example.library.integration;

import com.example.library.model.Book;
import com.example.library.model.Genre;
import com.example.library.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INTEGRATION TEST - Repository Layer
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
    }

    private Book createBook(String isbn, String title, String author, int copies, Genre genre) {
        Book book = new Book(isbn, title, author, copies, genre);
        book.setPublishedDate(LocalDate.of(2020, 1, 1));
        return bookRepository.save(book);
    }

    @Nested
    @DisplayName("Basic CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("should save and retrieve a book by ID")
        void shouldSaveAndFindById() {
            // Arrange: Persist a valid new book directly in the repository
            Book saved = createBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            // Act: Find the book by its generated ID
            Optional<Book> found = bookRepository.findById(saved.getId());

            // Assert: Verify that the book is present in database and matching details
            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("Clean Code");
            assertThat(found.get().getIsbn()).isEqualTo("978-0-13-468599-1");
        }

        @Test
        @DisplayName("should find book by ISBN")
        void shouldFindByIsbn() {
            // Arrange: Persist a book with a specific ISBN in the repository
            createBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            // Act: Find the book by its ISBN
            Optional<Book> found = bookRepository.findByIsbn("978-0-13-468599-1");

            // Assert: Verify that the book is found and its details are correct
            assertThat(found).isPresent();
            assertThat(found.get().getTitle()).isEqualTo("Clean Code");
        }

        @Test
        @DisplayName("should return empty when ISBN not found")
        void shouldReturnEmpty_WhenIsbnNotFound() {
            // Arrange: Ensure database does not contain a book with the query ISBN
            String nonExistentIsbn = "non-existent";

            // Act: Attempt to find a book by the non-existent ISBN
            Optional<Book> found = bookRepository.findByIsbn(nonExistentIsbn);

            // Assert: Verify that an empty Optional is returned
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Custom query methods")
    class CustomQueryTests {

        @Test
        @DisplayName("should search books by keyword in title or author (case insensitive)")
        void shouldSearchByKeyword() {
            // Arrange: Persist multiple books, two of which match the search keyword 'clean' in the title
            createBook("978-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
            createBook("978-2", "Clean Architecture", "Robert C. Martin", 2, Genre.TECHNOLOGY);
            createBook("978-3", "Design Patterns", "Gang of Four", 5, Genre.TECHNOLOGY);

            // Act: Search for books with the keyword "clean"
            List<Book> results = bookRepository.searchBooks("clean");

            // Assert: Verify that exactly the two matching books are returned
            assertThat(results).hasSize(2);
            assertThat(results).extracting(Book::getTitle)
                    .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
        }

        @Test
        @DisplayName("should find available books (copies > 0)")
        void shouldFindAvailableBooks() {
            // Arrange: Persist an available book, and an unavailable book with 0 available copies
            createBook("978-1", "Available Book", "Author A", 3, Genre.FICTION);
            Book unavailable = createBook("978-2", "Unavailable Book", "Author B", 1, Genre.FICTION);
            unavailable.setAvailableCopies(0);
            bookRepository.save(unavailable);

            // Act: Query for all available books in the repository
            List<Book> results = bookRepository.findAvailableBooks();

            // Assert: Verify that only the book with copies > 0 is returned
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Available Book");
        }
    }

    @Nested
    @DisplayName("Genre and author queries")
    class FilterTests {

        @Test
        @DisplayName("should find books by genre")
        void shouldFindByGenre() {
            // Arrange: Persist books with different genres
            createBook("978-S1", "Science Book", "Author S", 5, Genre.SCIENCE);
            createBook("978-H1", "History Book", "Author H", 2, Genre.HISTORY);

            // Act: Query for books belonging to the SCIENCE genre
            List<Book> results = bookRepository.findByGenre(Genre.SCIENCE);

            // Assert: Verify that only the matching book is returned
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getGenre()).isEqualTo(Genre.SCIENCE);
        }

        @Test
        @DisplayName("should find books by author (case insensitive, partial match)")
        void shouldFindByAuthor() {
            // Arrange: Persist a book with author "Joshua Bloch"
            createBook("978-A1", "Effective Java", "Joshua Bloch", 3, Genre.TECHNOLOGY);

            // Act: Query for books by author containing "bloch" (case-insensitive)
            List<Book> results = bookRepository.findByAuthorContainingIgnoreCase("bloch");

            // Assert: Verify that the matching book is found with the correct author
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getAuthor()).isEqualTo("Joshua Bloch");
        }

        @Test
        @DisplayName("should search by author name using searchBooks()")
        void shouldSearchByAuthorKeyword() {
            // Arrange: Persist a book written by Robert C. Martin
            createBook("978-A2", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            // Act: Search books using the author keyword "Martin"
            List<Book> results = bookRepository.searchBooks("Martin");

            // Assert: Verify that the book is returned and the author is correct
            assertThat(results).isNotEmpty();
            assertThat(results.get(0).getAuthor()).contains("Robert C. Martin");
        }

        @Test
        @DisplayName("should return empty list when no books match search")
        void shouldReturnEmpty_WhenNoMatch() {
            // Arrange: Persist a book that does not match the search keyword
            createBook("978-1", "Test Book", "Test Author", 1, Genre.FICTION);

            // Act: Search for a keyword that does not exist in any book
            List<Book> results = bookRepository.searchBooks("NonExistentKeyword");

            // Assert: Verify that an empty list is returned
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should enforce unique ISBN constraint")
        void shouldEnforceUniqueIsbn() {
            // Arrange: Persist a book with a specific ISBN, and create a second book with the same ISBN
            createBook("978-SAME", "First Book", "Author A", 1, Genre.FICTION);
            Book secondBook = new Book("978-SAME", "Second Book", "Author B", 1, Genre.FICTION);
            secondBook.setPublishedDate(LocalDate.now());

            // Act & Assert: Verify that saving the second book throws DataIntegrityViolationException
            assertThrows(DataIntegrityViolationException.class,
                    () -> bookRepository.saveAndFlush(secondBook));
        }

        @Test
        @DisplayName("should handle deleting a book")
        void shouldDeleteBook() {
            // Arrange: Persist a book to be deleted and capture its ID
            Book saved = createBook("978-DEL", "Delete Me", "Author X", 1, Genre.FICTION);
            Long id = saved.getId();

            // Act: Delete the book from repository and flush changes
            bookRepository.delete(saved);
            bookRepository.flush();
            Optional<Book> found = bookRepository.findById(id);

            // Assert: Verify that the book is no longer present in the database
            assertThat(found).isEmpty();
        }
    }
}