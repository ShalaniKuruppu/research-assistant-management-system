package com.rams.domain;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Binds a Java enum as a PostgreSQL enum. The driver rejects a plain varchar
 * for columns such as {@code user_role}, so each value is sent as a typed
 * {@link PGobject}.
 */
public abstract class PgEnumType<E extends Enum<E>> implements UserType<E> {

    private final Class<E> enumClass;
    private final String postgresType;

    protected PgEnumType(Class<E> enumClass, String postgresType) {
        this.enumClass = enumClass;
        this.postgresType = postgresType;
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<E> returnedClass() {
        return enumClass;
    }

    @Override
    public boolean equals(E left, E right) {
        return left == right;
    }

    @Override
    public int hashCode(E value) {
        return value == null ? 0 : value.hashCode();
    }

    @Override
    public E nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String raw = rs.getString(position);
        return raw == null ? null : Enum.valueOf(enumClass, raw);
    }

    @Override
    public void nullSafeSet(
            PreparedStatement statement,
            E value,
            int index,
            SharedSessionContractImplementor session
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }
        PGobject pg = new PGobject();
        pg.setType(postgresType);
        pg.setValue(value.name());
        statement.setObject(index, pg);
    }

    @Override
    public E deepCopy(E value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public E assemble(Serializable cached, Object owner) {
        return cached == null ? null : Enum.valueOf(enumClass, cached.toString());
    }

    public static final class UserRoleType extends PgEnumType<UserRole> {
        public UserRoleType() {
            super(UserRole.class, "user_role");
        }
    }

    public static final class ProjectStatusType extends PgEnumType<ProjectStatus> {
        public ProjectStatusType() {
            super(ProjectStatus.class, "project_status");
        }
    }

    public static final class TaskPriorityType extends PgEnumType<TaskPriority> {
        public TaskPriorityType() {
            super(TaskPriority.class, "task_priority");
        }
    }

    public static final class TaskStatusType extends PgEnumType<TaskStatus> {
        public TaskStatusType() {
            super(TaskStatus.class, "task_status");
        }
    }

    public static final class ReportStatusType extends PgEnumType<ReportStatus> {
        public ReportStatusType() {
            super(ReportStatus.class, "report_status");
        }
    }

    public static final class FeedbackOutcomeType extends PgEnumType<FeedbackOutcome> {
        public FeedbackOutcomeType() {
            super(FeedbackOutcome.class, "feedback_outcome");
        }
    }
}
