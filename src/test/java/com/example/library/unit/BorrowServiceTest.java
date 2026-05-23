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

    @Nested
    @DisplayName("borrowBook()")
    class BorrowBookTests {

        @Test
        @DisplayName("should successfully borrow a book when all conditions are met")
        void shouldBorrowBook_WhenAllConditionsMet() {
            // Arrange: Set up mock repositories to return valid member, book, zero active borrows, and simulate saving of the records
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

            // Act: Perform the borrow book operation for member ID 1 and book ID 1
            BorrowResponse response = borrowService.borrowBook(1L, 1L);

            // Assert: Verify that the response is not null and contains the expected details, and that mock interactions occurred
            assertNotNull(response);
            assertEquals("Clean Code", response.getBookTitle());
            assertEquals("Alice", response.getMemberName());
            assertEquals(BorrowStatus.BORROWED, response.getStatus());
            verify(borrowRecordRepository).save(any(BorrowRecord.class));
            verify(bookRepository).save(any(Book.class));
        }

        @Test
        @DisplayName("should throw MemberNotFoundException when member does not exist")
        void shouldThrow_WhenMemberNotFound() {
            // Arrange: Configure member repository to return empty for the search ID
            when(memberRepository.findById(99L)).thenReturn(Optional.empty());

            // Act: Attempt to borrow a book with a non-existent member ID
            // Assert: Verify that a MemberNotFoundException is thrown and no borrow record is saved
            assertThrows(MemberNotFoundException.class,
                    () -> borrowService.borrowBook(1L, 99L));
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when book has no available copies")
        void shouldThrow_WhenNoAvailableCopies() {
            // Arrange: Set available copies of the book to 0 and configure mock repositories to return the member and book
            sampleBook.setAvailableCopies(0);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

            // Act: Attempt to borrow the book with zero available copies
            // Assert: Verify that a BookNotAvailableException is thrown
            assertThrows(BookNotAvailableException.class,
                    () -> borrowService.borrowBook(1L, 1L));
        }

        @Test
        @DisplayName("should throw when member has reached borrowing limit")
        void shouldThrow_WhenBorrowLimitReached() {
            // Arrange: Configure mocks to return standard member Alice and show she has reached her limit of 3 active borrows
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(3);

            // Act: Attempt to borrow another book when limit is reached
            BorrowLimitExceededException exception = assertThrows(BorrowLimitExceededException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            // Assert: Verify that a BorrowLimitExceededException is thrown with the correct message and no new record is saved
            assertEquals("Alice has reached the borrowing limit of 3 books", exception.getMessage());
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when member already has this book borrowed")
        void shouldThrow_WhenDuplicateBorrow() {
            // Arrange: Configure mocks to show member has already borrowed this specific book
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(true);

            // Act: Attempt to borrow the same book again
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            // Assert: Verify that an IllegalStateException is thrown with the correct message and no save occurs
            assertEquals("Member already has this book borrowed", exception.getMessage());
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when inactive member tries to borrow")
        void shouldThrow_WhenMemberInactive() {
            // Arrange: Mark sampleMember as inactive, configure mock repository to return the inactive member, and configure book leniently
            sampleMember.setActive(false);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            lenient().when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

            // Act: Attempt to borrow a book with the inactive member
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            // Assert: Verify that an IllegalStateException is thrown indicating the member is inactive
            assertEquals("Inactive members cannot borrow books", exception.getMessage());
        }

        @Test
        @DisplayName("should decrease available copies after successful borrow")
        void shouldDecreaseAvailableCopies() {
            // Arrange: Set up a full happy-path mock environment and initialize an ArgumentCaptor for the Book instance
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);
            when(borrowRecordRepository.save(any(BorrowRecord.class)))
                    .thenAnswer(invocation -> {
                        BorrowRecord record = invocation.getArgument(0);
                        record.setId(100L);
                        return record;
                    });
            when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);

            // Act: Borrow the book
            borrowService.borrowBook(1L, 1L);

            // Assert: Verify that the saved book has its available copies decremented from 3 to 2
            verify(bookRepository).save(bookCaptor.capture());
            Book savedBook = bookCaptor.getValue();
            assertEquals(2, savedBook.getAvailableCopies());
        }
    }

    @Nested
    @DisplayName("returnBook()")
    class ReturnBookTests {

        @Test
        @DisplayName("should successfully return a borrowed book")
        void shouldReturnBook_WhenBorrowed() {
            // Arrange: Create an active borrow record, configure repo mock to return it, and record initial copies
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));
            int initialCopies = sampleBook.getAvailableCopies();

            // Act: Return the book
            BorrowResponse response = borrowService.returnBook(1L);

            // Assert: Verify that the book is marked as returned, copies are incremented by 1, and changes saved
            assertEquals(BorrowStatus.RETURNED, response.getStatus());
            assertEquals(LocalDate.now(), response.getReturnDate());
            verify(bookRepository).save(sampleBook);
            assertEquals(initialCopies + 1, sampleBook.getAvailableCopies());
        }

        @Test
        @DisplayName("should throw when trying to return an already returned book")
        void shouldThrow_WhenAlreadyReturned() {
            // Arrange: Create a borrow record that has already been returned, and configure repo mock to return it
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            record.setStatus(BorrowStatus.RETURNED);
            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

            // Act: Attempt to return the already returned book
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.returnBook(1L));

            // Assert: Verify that an IllegalStateException is thrown with the correct message
            assertEquals("This book has already been returned", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when borrow record not found")
        void shouldThrow_WhenRecordNotFound() {
            // Arrange: Configure mock repository to return empty for the borrow record search
            when(borrowRecordRepository.findById(99L)).thenReturn(Optional.empty());

            // Act: Attempt to return a book using a non-existent borrow record ID
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.returnBook(99L));

            // Assert: Verify that an IllegalStateException is thrown indicating the record is missing
            assertEquals("Borrow record not found: 99", exception.getMessage());
        }
    }
}
