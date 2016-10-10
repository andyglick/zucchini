/**
 * Copyright 2014 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.comcast.zucchini;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by djerus200 on 5/4/16.
 */
public class CleanupTest {

    @Test
    public void testCleanup() throws Throwable {

        final AtomicBoolean cleanupFlag = new AtomicBoolean(false);

        AbstractZucchiniTest azt = new AbstractZucchiniTest() {
            private static final String TCNAME = "testCleanupContext";

            @Override
            public List<TestContext> getTestContexts() {
                return Arrays.asList(new TestContext(TCNAME));
            }

            @Override
            public void setup(TestContext out) {
                throw new RuntimeException("Failing on purpose to test that cleanup will be invoked");
            }

            @Override
            public void cleanup(TestContext out) {
                cleanupFlag.set(true);
            }

            @Override
            public boolean isParallel() {
                return false;

            }

            @Override
            List<String> ignoredTests() {
                return Arrays.asList(TCNAME);
            }
        };

        try {
            azt.run();
            Assert.fail("Should've failed");
        }
        catch (AssertionError e) {
            if ("Should've failed".equals(e.getMessage())) {
                throw e;
            }
            // swallow exception as we expect test to fail due to exception in 'setup'
        }

        Assert.assertTrue(cleanupFlag.get(), "Cleanup was never run");
    }
}
