package edu.northeastern.courseassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Data model representing a Northeastern University course
 */
public class Course {
    private String title;
    private String crn;
    private String subject;
    private String number;
    private String section;
    private String campus;
    private String credits;
    private String instructor;
    private String time;
    private String days;
    private String location;
    private String available;
    private String capacity;
    private String enrolled;

    /**
     * Default constructor for Jackson deserialization
     */
    public Course() {
    }

    /**
     * Full constructor for all course properties
     */
    public Course(String title, String crn, String subject, String number, String section,
                  String campus, String credits, String instructor, String time, String days,
                  String location, String available, String capacity, String enrolled) {
        this.title = title;
        this.crn = crn;
        this.subject = subject;
        this.number = number;
        this.section = section;
        this.campus = campus;
        this.credits = credits;
        this.instructor = instructor;
        this.time = time;
        this.days = days;
        this.location = location;
        this.available = available;
        this.capacity = capacity;
        this.enrolled = enrolled;
    }

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCrn() {
        return crn;
    }

    public void setCrn(String crn) {
        this.crn = crn;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getCampus() {
        return campus;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }

    public String getCredits() {
        return credits;
    }

    public void setCredits(String credits) {
        this.credits = credits;
    }

    public String getInstructor() {
        return instructor;
    }

    public void setInstructor(String instructor) {
        this.instructor = instructor;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDays() {
        return days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getAvailable() {
        return available;
    }

    public void setAvailable(String available) {
        this.available = available;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getEnrolled() {
        return enrolled;
    }

    public void setEnrolled(String enrolled) {
        this.enrolled = enrolled;
    }

    /**
     * Converts day codes to readable format
     * @return Human-readable day format
     */
    public String getReadableDays() {
        if (days == null || days.isEmpty()) {
            return "N/A";
        }

        StringBuilder result = new StringBuilder();
        for (char day : days.toCharArray()) {
            switch (day) {
                case 'M':
                    result.append("Monday");
                    break;
                case 'T':
                    result.append("Tuesday");
                    break;
                case 'W':
                    result.append("Wednesday");
                    break;
                case 'R':
                    result.append("Thursday");
                    break;
                case 'F':
                    result.append("Friday");
                    break;
                case 'S':
                    result.append("Saturday");
                    break;
                case 'U':
                    result.append("Sunday");
                    break;
                default:
                    result.append(day);
            }
            result.append(", ");
        }

        // Remove trailing comma and space
        if (result.length() > 2) {
            result.setLength(result.length() - 2);
        }

        return result.toString();
    }

    /**
     * Checks if the course is available for enrollment
     * @return true if there are available seats, false otherwise
     */
    public boolean isAvailable() {
        try {
            int availableSeats = Integer.parseInt(available);
            return availableSeats > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Gets the availability status as a formatted string
     * @return Availability string
     */
    public String getAvailabilityStatus() {
        try {
            int availableSeats = Integer.parseInt(available);
            int capacitySeats = Integer.parseInt(capacity);
            int enrolledCount = Integer.parseInt(enrolled);

            if (availableSeats > 10) {
                return "Open - " + availableSeats + " seats available";
            } else if (availableSeats > 0) {
                return "Limited - Only " + availableSeats + " seats available";
            } else {
                return "Closed - No seats available";
            }
        } catch (NumberFormatException e) {
            return "Unknown availability";
        }
    }

    /**
     * Creates a formatted display string for the course
     * @return String representation of the course details
     */
    public String toFormattedString() {
        return String.format(
                "%s %s-%s: %s\n" +
                        "Instructor: %s\n" +
                        "Schedule: %s %s in %s\n" +
                        "Availability: %s/%s (%s seats available)",
                subject, number, section, title,
                instructor,
                getReadableDays(), time, location,
                enrolled, capacity, available
        );
    }

    /**
     * Creates a compact single-line representation of the course
     * @return Compact string representation
     */
    public String toCompactString() {
        return String.format(
                "%s %s-%s: %s - %s - %s",
                subject, number, section, title,
                getReadableDays() + " " + time,
                getAvailabilityStatus()
        );
    }

    /**
     * Creates HTML representation for display in web UI
     * @return HTML string for the course
     */
    public String toHtml() {
        String availabilityClass = isAvailable() ? "available" : "unavailable";

        return String.format(
                "<div class='course-card %s'>" +
                        "  <div class='course-header'>" +
                        "    <h3>%s %s-%s</h3>" +
                        "    <span class='course-availability'>%s</span>" +
                        "  </div>" +
                        "  <h4>%s</h4>" +
                        "  <div class='course-details'>" +
                        "    <p><strong>Instructor:</strong> %s</p>" +
                        "    <p><strong>Schedule:</strong> %s %s</p>" +
                        "    <p><strong>Location:</strong> %s</p>" +
                        "    <p><strong>Campus:</strong> %s</p>" +
                        "    <p><strong>Credits:</strong> %s</p>" +
                        "    <p><strong>Enrollment:</strong> %s/%s (%s seats available)</p>" +
                        "  </div>" +
                        "</div>",
                availabilityClass,
                subject, number, section,
                getAvailabilityStatus(),
                title,
                instructor,
                getReadableDays(), time,
                location,
                campus,
                credits,
                enrolled, capacity, available
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return Objects.equals(crn, course.crn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(crn);
    }
}