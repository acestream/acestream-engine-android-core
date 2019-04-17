package org.acestream.engine.testing.suite;

import org.acestream.engine.testing.EngineServiceTest;
import org.acestream.engine.testing.MRLDialogTest;
import org.acestream.engine.testing.PlayerDetectionTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        EngineServiceTest.class,
        MRLDialogTest.class,
        PlayerDetectionTest.class,
})
public class AppTestSuite {}
