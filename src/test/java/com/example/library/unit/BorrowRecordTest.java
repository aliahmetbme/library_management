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

    // =========================================================================
    // EXAMPLE: calculateFine() tests — filled in as reference
    // =========================================================================

    @Nested
    @DisplayName("calculateFine()")
    class CalculateFineTests {

        @Test
        @DisplayName("should return 0 when book is returned on time")
        void shouldReturnZeroFine_WhenReturnedOnTime() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getDueDate()); // returned exactly on due date

            assertEquals(0.0, record.calculateFine());
        }

        @Test
        @DisplayName("should return 0 when book is returned before due date")
        void shouldReturnZeroFine_WhenReturnedEarly() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getBorrowDate().plusDays(5)); // returned after 5 days

            assertEquals(0.0, record.calculateFine());
        }

        @Test
        @DisplayName("should calculate correct fine when returned 3 days late")
        void shouldCalculateCorrectFine_WhenReturnedLate() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getDueDate().plusDays(3)); // 3 days late

            double expectedFine = 3 * BorrowRecord.DAILY_FINE_RATE; // 3 * 1.50 = 4.50
            assertEquals(expectedFine, record.calculateFine());
        }

        @Test
        @DisplayName("should return 0 when book is not yet returned")
        void shouldReturnZeroFine_WhenNotYetReturned() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            // returnDate is null

            assertEquals(0.0, record.calculateFine());
        }
    }

    // =========================================================================
    // TODO: Students should write these tests
    // =========================================================================

    @Nested
    @DisplayName("isOverdue()")
    class IsOverdueTests {

        @Test
        @DisplayName("should return true when checked after due date and still borrowed")
        void shouldBeOverdue_WhenPastDueDateAndStillBorrowed() {
            // Arrange: Test the one day after the due day
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            LocalDate afterDueDate = borrowRecord.getDueDate().plusDays(1);

            // Act
            boolean isOverdue = borrowRecord.isOverdue(afterDueDate);

            // Assert: Should be false because both the due date is exceeded and the book did not be rebate
            assertTrue(isOverdue, "The due date should be exceed");
        }

        @Test
        @DisplayName("should return false when checked before due date")
        void shouldNotBeOverdue_WhenBeforeDueDate() {
            // Arrange: Simulate the one day before from due day
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            LocalDate beforeDueDate = borrowRecord.getDueDate().minusDays(1);

            // Act
            boolean isOverDue = borrowRecord.isOverdue(beforeDueDate);

            // Assert: Return false because the date not be arrived
            assertFalse(isOverDue,"It should not be considered late before the delivery date.");
        }

        @Test
        @DisplayName("should return false when book is already returned (even if past due)")
        void shouldNotBeOverdue_WhenAlreadyReturned() {
            // Arrange: Even if the date has passed, we mark the book as "returned".
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(), createSampleMember());
            borrowRecord.setStatus(BorrowStatus.RETURNED);
            LocalDate afterDueDate = borrowRecord.getDueDate().plusDays(5);

            // Act
            boolean isOverDue = borrowRecord.isOverdue(afterDueDate);


            // Assert: Should be returned as false, because the book already returned
            assertFalse(isOverDue,"Should be returned as false, because the book already returned");
        }

        @Test
        @DisplayName("should return false on exactly the due date")
        void shouldNotBeOverdue_OnExactDueDate() {
            // Arrange: The exact due day must not be accepted as overdue
            BorrowRecord borrowRecord = new BorrowRecord(createSampleBook(),createSampleMember());
            LocalDate exactDueDate = borrowRecord.getDueDate();

            // Act
            boolean isOverdue = borrowRecord.isOverdue(exactDueDate);

            // Assert
            assertFalse(isOverdue,"It should not be considered late by the exact delivery date.");

        }
    }

    @Nested
    @DisplayName("Constructor / default values")
    class ConstructorTests {

        @Test
        @DisplayName("should set borrow date to today")
        void shouldSetBorrowDateToToday() {
            // Arrange: prepare required test data
            Book book = createSampleBook();
            Member member = createSampleMember();

            // Act: Call the method should be tested
            BorrowRecord record = new BorrowRecord(book, member);

            // Assert: validate the result
            assertEquals(LocalDate.now(), record.getBorrowDate(),"The borrow date should be today");
        }

        @Test
        @DisplayName("should set due date to 14 days from today")
        void shouldSetDueDateTo14DaysFromToday() {
            // Arrange: prepare required test data
            Book book = createSampleBook();
            Member member = createSampleMember();

            // Act: Call the method should be tested
            BorrowRecord record = new BorrowRecord(book, member);

            // Assert: validate the result
            assertEquals(LocalDate.now().plusDays(BorrowRecord.STANDARD_BORROW_DAYS), record.getDueDate(),"The due day must be 14 days from the borrow date");


        }

        @Test
        @DisplayName("should set status to BORROWED")
        void shouldSetStatusToBorrowed() {
            // Arrange
            Book book = createSampleBook();
            Member member = createSampleMember();

            // Act
            BorrowRecord record = new BorrowRecord(book, member);

            // Assert: validate the result
            assertEquals(BorrowStatus.BORROWED, record.getStatus(),"The status changed to 'BORROWED'");
        }
    }
}
