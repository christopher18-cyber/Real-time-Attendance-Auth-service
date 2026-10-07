package com.genius.repo;

import com.genius.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseCode(String courseCode);
    List<Course> findByLecturerId(Long lecturerId);
    @Query("select distinct c from Course c join c.supportingLecturerEmails email where lower(email) = lower(:email)")
    List<Course> findSupportingCourses(@Param("email") String email);
}
