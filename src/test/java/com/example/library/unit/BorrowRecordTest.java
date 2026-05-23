package com.example.library.unit;

import com.example.library.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UNIT TEST - Model Layer
 */
class BorrowRecordTest {

    private Book createSampleBook() {
        Book book = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
        book.setId(1L);
        return book;
    }

    private Member createSampleMember() {
        Member member = new Member("Alice", "alice@example.com", MembershipType.STANDARD);
        member.setId(1L);
        return member;
    }

    @Nested
    @DisplayName("calculateFine()")
    class CalculateFineTests {

        @Test
        @DisplayName("should return 0 when book is returned on time")
        void shouldReturnZeroFine_WhenReturnedOnTime() {
            // Arrange: Create a borrow record for a sample book and member, then set the return date to the exact due date
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getDueDate());

            // Act: Calculate the fine for the borrow record
            double fine = record.calculateFine();

            // Assert: Verify that the fine is 0.0 since it was returned on time
            assertEquals(0.0, fine);
        }

        @Test
        @DisplayName("should return 0 when book is returned before due date")
        void shouldReturnZeroFine_WhenReturnedEarly() {
            // Arrange: Create a borrow record and set the return date to 5 days after borrowing, which is well before the 14-day due date
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getBorrowDate().plusDays(5));

            // Act: Calculate the fine for returning the book early
            double fine = record.calculateFine();

            // Assert: Verify that the fine is 0.0 since the return is not late
            assertEquals(0.0, fine);
        }

        @Test
        @DisplayName("should calculate correct fine when returned 3 days late")
        void shouldCalculateCorrectFine_WhenReturnedLate() {
            // Arrange: Create a borrow record and set the return date to 3 days after the due date (late return)
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getDueDate().plusDays(3));
            double expectedFine = 3 * BorrowRecord.DAILY_FINE_RATE;

            // Act: Calculate the late fine for this record
            double fine = record.calculateFine();

            // Assert: Verify that the fine matches the expected rate for 3 days late (4.50 TL)
            assertEquals(expectedFine, fine);
        }

        @Test
        @DisplayName("should return 0 when book is not yet returned")
        void shouldReturnZeroFine_WhenNotYetReturned() {
            // Arrange: Create a borrow record where returnDate is left as null (book is still borrowed)
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            // Act: Calculate the fine for a book that has not been returned yet
            double fine = record.calculateFine();

            // Assert: Verify that no fine is calculated (0.0) while the book is active and not returned
            assertEquals(0.0, fine);
        }
    }

    @Nested
    @DisplayName("isOverdue()")
    class IsOverdueTests {

        @Test
        @DisplayName("should return true when checked after due date and still borrowed")
        void shouldBeOverdue_WhenPastDueDateAndStillBorrowed() {
            // Arrange: Create a borrow record and set the check date to one day past the due date
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            LocalDate afterDueDate = borrowRecord.getDueDate().plusDays(1);

            // Act: Check if the borrow record is overdue on that date
            boolean isOverdue = borrowRecord.isOverdue(afterDueDate);

            // Assert: Verify that the record is indeed overdue
            assertTrue(isOverdue, "Record should be overdue when checked one day after the due date");
        }

        @Test
        @DisplayName("should return false when checked before due date")
        void shouldNotBeOverdue_WhenBeforeDueDate() {
            // Arrange: Create a borrow record and set the check date to one day before the due date
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            LocalDate beforeDueDate = borrowRecord.getDueDate().minusDays(1);

            // Act: Check if the borrow record is overdue before its due date
            boolean isOverdue = borrowRecord.isOverdue(beforeDueDate);

            // Assert: Verify that the record is not overdue
            assertFalse(isOverdue, "It should not be considered late before the delivery date.");
        }

        @Test
        @DisplayName("should return false when book is already returned (even if past due)")
        void shouldNotBeOverdue_WhenAlreadyReturned() {
            // Arrange: Create a borrow record, mark it as returned, and set the check date to five days past the due date
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            borrowRecord.setStatus(BorrowStatus.RETURNED);
            LocalDate afterDueDate = borrowRecord.getDueDate().plusDays(5);

            // Act: Check if the borrow record is overdue when it has already been returned
            boolean isOverdue = borrowRecord.isOverdue(afterDueDate);

            // Assert: Verify that the record is not overdue because the book was returned
            assertFalse(isOverdue, "Should be returned as false, because the book already returned");
        }

        @Test
        @DisplayName("should return false on exactly the due date")
        void shouldNotBeOverdue_OnExactDueDate() {
            // Arrange: Create a borrow record and set the check date to the exact due date
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            LocalDate exactDueDate = borrowRecord.getDueDate();

            // Act: Check if the borrow record is overdue exactly on the due date
            boolean isOverdue = borrowRecord.isOverdue(exactDueDate);

            // Assert: Verify that the record is not overdue on the due date itself
            assertFalse(isOverdue, "It should not be considered late by the exact delivery date.");
        }
    }

    @Nested
    @DisplayName("Constructor / default values")
    class ConstructorTests {

        @Test
        @DisplayName("should set borrow date to today")
        void shouldSetBorrowDateToToday() {
            // Arrange: Prepare a sample book and member for construction
            Book book = createSampleBook();
            Member member = createSampleMember();

            // Act: Instantiate a new BorrowRecord
            BorrowRecord record = new BorrowRecord(book, member);

            // Assert: Verify that the borrow date is set to the current date
            assertEquals(LocalDate.now(), record.getBorrowDate(), "The borrow date should be today");
        }

        @Test
        @DisplayName("should set due date to 14 days from today")
        void shouldSetDueDateTo14DaysFromToday() {
            // Arrange: Prepare a sample book and member for construction
            Book book = createSampleBook();
            Member member = createSampleMember();

            // Act: Instantiate a new BorrowRecord
            BorrowRecord record = new BorrowRecord(book, member);

            // Assert: Verify that the due date is exactly 14 days after the current date
            assertEquals(LocalDate.now().plusDays(BorrowRecord.STANDARD_BORROW_DAYS), record.getDueDate(), "The due day must be 14 days from the borrow date");
        }

        @Test
        @DisplayName("should set status to BORROWED")
        void shouldSetStatusToBorrowed() {
            // Arrange: Prepare a sample book and member for construction
            Book book = createSampleBook();
            Member member = createSampleMember();

            // Act: Instantiate a new BorrowRecord
            BorrowRecord record = new BorrowRecord(book, member);

            // Assert: Verify that the borrow record's status is defaulted to BORROWED
            assertEquals(BorrowStatus.BORROWED, record.getStatus(), "The status changed to 'BORROWED'");
        }
    }
}
