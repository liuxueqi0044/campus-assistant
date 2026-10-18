-- Test-only fixture. It is never copied to a production JAR.
INSERT INTO campus_school (id, code, name, timezone, status, config_version, sort_order)
VALUES
  (1, 'S1', '示例大学 S1', 'Asia/Shanghai', 'ENABLED', 1, 10),
  (2, 'S2', '示例大学 S2', 'Asia/Shanghai', 'ENABLED', 1, 20);

INSERT INTO campus_campus (id, school_id, code, name, timezone, status, config_version, sort_order)
VALUES
  (11, 1, 'C11', 'S1 主校区', 'Asia/Shanghai', 'ENABLED', 1, 10),
  (12, 1, 'C12', 'S1 北校区', 'Asia/Shanghai', 'ENABLED', 1, 20),
  (21, 2, 'C21', 'S2 主校区', 'Asia/Shanghai', 'ENABLED', 1, 10);
