package com.example.library.unit;

import com.example.library.dto.BorrowResponse;
import com.example.library.exception.*;
import com.example.library.model.*;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowRecordRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UNIT TEST - Service Layer
 */
@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private BorrowService borrowService;

    private Book sampleBook;
    private Member sampleMember;

    @BeforeEach
    void setUp() {
        sampleBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
        sampleBook.setId(1L);
        sampleBook.setAvailableCopies(3);

        sampleMember = new Member("Alice", "alice@example.com", MembershipType.STANDARD);
        sampleMember.setId(1L);
    }

    // =========================================================================
    // EXAMPLE: borrowBook() happy path and key error cases — filled in
    // =========================================================================

    @Nested
    @DisplayName("borrowBook()")
    class BorrowBookTests {

        @Test
        @DisplayName("should successfully borrow a book when all conditions are met")
        void shouldBorrowBook_WhenAllConditionsMet() {
            // Arrange
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);
            when(borrowRecordRepository.save(any(BorrowRecord.class)))
                    .thenAnswer(invocation -> {
                        BorrowRecord record = invocation.getArgument(0);
                        record.setId(1L);
                        return record;
                    });
            when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);

            // Act
            BorrowResponse response = borrowService.borrowBook(1L, 1L);

            // Assert
            assertNotNull(response);
            assertEquals("Clean Code", response.getBookTitle());
            assertEquals("Alice", response.getMemberName());
            assertEquals(BorrowStatus.BORROWED, response.getStatus());

            // Verify interactions
            verify(borrowRecordRepository).save(any(BorrowRecord.class));
            verify(bookRepository).save(any(Book.class));
        }

        @Test
        @DisplayName("should throw MemberNotFoundException when member does not exist")
        void shouldThrow_WhenMemberNotFound() {
            when(memberRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class,
                    () -> borrowService.borrowBook(1L, 99L));

            // Verify no borrow record was saved
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when book has no available copies")
        void shouldThrow_WhenNoAvailableCopies() {
            sampleBook.setAvailableCopies(0);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

            assertThrows(BookNotAvailableException.class,
                    () -> borrowService.borrowBook(1L, 1L));
        }

        // =====================================================================
        // TODO: Students should write the remaining borrowBook() tests
        // =====================================================================

        @Test
        @DisplayName("should throw when member has reached borrowing limit")
        void shouldThrow_WhenBorrowLimitReached() {
            // Arrange: simulate the member's limit (3 for standard)
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(3);

            // Act and Assert: should throw a BorrowLimitExceededException when trying to buy a new book
            BorrowLimitExceededException exception = assertThrows(BorrowLimitExceededException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            // Extra Assert: confirm the truth of the error message
            assertEquals("Alice has reached the borrowing limit of 3 books", exception.getMessage());

            // verify that the db record method was never called (isolation)
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when member already has this book borrowed")
        void shouldThrow_WhenDuplicateBorrow() {
            // Arrange: simulate the member has already borrowed this book
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(true);

            // Act & Assert: should throw an IllegalStateException when try to get the same book again.
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            assertEquals("Member already has this book borrowed", exception.getMessage());
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when inactive member tries to borrow")
        void shouldThrow_WhenMemberInactive() {
            // Arrange: make member inactive
            sampleMember.setActive(false);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));

            // Act & Assert: IllegalStateException must be thrown when a passive member attempts to purchase a book.
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            assertEquals("Inactive members cannot borrow books", exception.getMessage());
        }

        @Test
        @DisplayName("should decrease available copies after successful borrow")
        void shouldDecreaseAvailableCopies() {
            // Arrange: preparing a successful loan scenario.
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);

            // ArgumentCaptor with bookRepository.save() capturing the Book object that goes to the method
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);

            // Act: Borrow the book
            borrowService.borrowBook(1L, 1L);

            // Assert: bookRepository.save() verify that the method was called and capture the outgoing object.
            verify(bookRepository).save(bookCaptor.capture());
            Book savedBook = bookCaptor.getValue();

            // Confirm that the number of copies, initially 3, has been reduced to 2.
            assertEquals(2, savedBook.getAvailableCopies());
        }
    }

    // =========================================================================
    // TODO: Students should write returnBook() tests
    // =========================================================================

    @Nested
    @DisplayName("returnBook()")
    class ReturnBookTests {

        @Test
        @DisplayName("should successfully return a borrowed book")
        void shouldReturnBook_WhenBorrowed() {
            // Arrange: Create an active borrowing record.
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

            int initialCopies = sampleBook.getAvailableCopies(); //

            // Act: return the book
            BorrowResponse response = borrowService.returnBook(1L);

            // Assert: Confirm that the status is RETURNED and the return date has been set to today.
            assertEquals(BorrowStatus.RETURNED, response.getStatus());
            assertEquals(LocalDate.now(), response.getReturnDate());

            // We confirm that the number of book copies has increased by 1.
            verify(bookRepository).save(sampleBook);
            assertEquals(initialCopies + 1, sampleBook.getAvailableCopies());
        }

        @Test
        @DisplayName("should throw when trying to return an already returned book")
        void shouldThrow_WhenAlreadyReturned() {
            // Arrange: Create a record that has already been returned.
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            record.setStatus(BorrowStatus.RETURNED);
            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

            // Act & Assert: It should throw an IllegalStateException when it tries to return it again.
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.returnBook(1L));

            assertEquals("This book has already been returned", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when borrow record not found")
        void shouldThrow_WhenRecordNotFound() {
            // Arrange: Simulate that the record cannot be found in the database.
            when(borrowRecordRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.returnBook(99L));

            assertEquals("Borrow record not found: 99", exception.getMessage());
        }
    }
}
