package com.planet.importexport.importapi.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * CDI qualifier for the bounded {@link java.util.concurrent.ExecutorService}
 * produced by {@link ImportExecutorProducer} and used to dispatch asynchronous
 * import job processing (ADR-0002).
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface ImportExecutor {}
