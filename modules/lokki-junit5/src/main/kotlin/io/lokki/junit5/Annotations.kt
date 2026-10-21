package io.lokki.junit5

import java.lang.annotation.Inherited

@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class KeepMocks

@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class ConfirmVerification

@Inherited
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class CheckUnnecessaryStub
