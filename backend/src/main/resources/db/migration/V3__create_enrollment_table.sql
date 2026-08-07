CREATE TABLE enrollment (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id   VARCHAR(255) NOT NULL,
  course_id    BIGINT NOT NULL,
  enrolled_at  DATETIME NOT NULL
);