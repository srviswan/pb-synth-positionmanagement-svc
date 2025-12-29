package com.bank.esps.infrastructure.persistence.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.beans.factory.annotation.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for database-agnostic JSON columns
 * Automatically uses JSONB for PostgreSQL and NVARCHAR(MAX) for MS SQL Server
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JdbcTypeCode(SqlTypes.JSON)
public @interface DatabaseAwareJsonColumn {
    /**
     * Column definition for PostgreSQL (JSONB)
     */
    String postgresql() default "jsonb";
    
    /**
     * Column definition for MS SQL Server (NVARCHAR(MAX))
     */
    String sqlserver() default "nvarchar(max)";
}
