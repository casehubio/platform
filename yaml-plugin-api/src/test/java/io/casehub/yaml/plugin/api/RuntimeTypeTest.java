package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeTypeTest {

    @Test
    void javaCanExecuteUniversal() { assertTrue(RuntimeType.JAVA.canExecute(Portability.UNIVERSAL)); }

    @Test
    void javaCanExecuteJava() { assertTrue(RuntimeType.JAVA.canExecute(Portability.JAVA)); }

    @Test
    void javaCannotExecuteTs() { assertFalse(RuntimeType.JAVA.canExecute(Portability.TS)); }

    @Test
    void javaCanExecuteBoth() { assertTrue(RuntimeType.JAVA.canExecute(Portability.BOTH)); }

    @Test
    void tsCanExecuteUniversal() { assertTrue(RuntimeType.TS.canExecute(Portability.UNIVERSAL)); }

    @Test
    void tsCannotExecuteJava() { assertFalse(RuntimeType.TS.canExecute(Portability.JAVA)); }

    @Test
    void tsCanExecuteTs() { assertTrue(RuntimeType.TS.canExecute(Portability.TS)); }

    @Test
    void tsCanExecuteBoth() { assertTrue(RuntimeType.TS.canExecute(Portability.BOTH)); }
}
