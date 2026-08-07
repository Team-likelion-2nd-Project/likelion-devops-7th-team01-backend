CREATE TABLE course (
  id          BIGINT PRIMARY KEY,
  course_code VARCHAR(20) NOT NULL,
  name        VARCHAR(255) NOT NULL,
  professor   VARCHAR(100),
  department  VARCHAR(100),
  capacity    INT NOT NULL,
  remaining   INT NOT NULL,
  day_of_week VARCHAR(255),
  start_time  VARCHAR(255),
  end_time    VARCHAR(255)
);