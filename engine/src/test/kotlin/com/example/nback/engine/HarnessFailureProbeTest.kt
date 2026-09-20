package com.example.nback.engine

import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises the harness's failure path; this is not a gameplay correctness test. */
class HarnessFailureProbeTest {
    @Test
    fun deliberateFailureIsReported() {
        assumeTrue(java.lang.Boolean.getBoolean("harness.failureProbe"))
        fail("HARNESS_EXPECTED_FAILURE")
    }
}
