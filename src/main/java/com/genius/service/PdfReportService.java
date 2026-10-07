package com.genius.service;

import com.genius.dto.CourseView;
import com.genius.dto.SessionView;
import com.genius.model.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class PdfReportService {
    private final AttendanceService attendanceService;

    public PdfReportService(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public byte[] generateSessionAttendancePdf(Long sessionId, User lecturer) throws IOException {
        SessionView session = attendanceService.getReportSession(sessionId, lecturer);
        Map<String, SessionView.CheckInView> present = new HashMap<>();
        for (SessionView.CheckInView checkIn : session.checkIns()) {
            present.put(key(checkIn.email(), checkIn.matricNo()), checkIn);
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPageContentStream stream = null;
            int y = 0;
            try {
                for (CourseView.RosterEntry row : session.rosterSnapshot()) {
                    if (stream == null || y < 56) {
                        if (stream != null) stream.close();
                        document.addPage(new PDPage());
                        stream = new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1));
                        write(stream, bold, 16, 48, 750, "Attendance report: " + session.courseCode());
                        write(stream, regular, 10, 48, 730,
                                "Session " + session.id() + " | " + session.createdAt().substring(0, 10) +
                                        " | Present " + session.presentCount() + " / " + session.rosterCount());
                        write(stream, bold, 10, 48, 700, "Student");
                        write(stream, bold, 10, 285, 700, "Matric number");
                        write(stream, bold, 10, 425, 700, "Status");
                        y = 680;
                    }
                    SessionView.CheckInView checkIn = present.get(key(row.email(), row.matricNo()));
                    write(stream, regular, 9, 48, y, limit(row.name(), 42));
                    write(stream, regular, 9, 285, y, limit(row.matricNo(), 24));
                    write(stream, regular, 9, 425, y, checkIn == null ? "ABSENT" : "PRESENT");
                    y -= 18;
                }
                if (stream == null) {
                    document.addPage(new PDPage());
                    stream = new PDPageContentStream(document, document.getPage(0));
                    write(stream, bold, 16, 48, 750, "Attendance report: " + session.courseCode());
                    write(stream, regular, 10, 48, 720, "No students were in this session's roster.");
                }
            } finally {
                if (stream != null) stream.close();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String key(String email, String matricNo) {
        return email.toLowerCase() + "|" + matricNo.toLowerCase();
    }

    private static String limit(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length - 3) + "...";
    }

    private static void write(PDPageContentStream stream, PDType1Font font, int size,
                              int x, int y, String value) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value.replaceAll("[^\\x20-\\x7E]", "?"));
        stream.endText();
    }
}
