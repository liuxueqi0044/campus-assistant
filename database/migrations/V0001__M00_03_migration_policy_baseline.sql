-- M00.03 establishes the first checksum-protected Flyway history without claiming ownership of
-- any business table. M01+ modules append their owned schema from V0002 onward.
-- This no-op statement is intentional: it makes the policy marker a valid, auditable Flyway SQL
-- migration while production automatic schema creation remains disabled.
SELECT 1;
