package com.investmentcopilot.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingTest {

    @Test
    void prePersist_sets_createdAt_and_updatedAt() throws Exception {
        Holding h = new Holding();
        invokePrePersist(h);

        assertThat(h.getCreatedAt()).isNotNull();
        assertThat(h.getUpdatedAt()).isNotNull();
        assertThat(h.getCreatedAt()).isEqualTo(h.getUpdatedAt());
    }

    @Test
    void preUpdate_only_advances_updatedAt() throws Exception {
        Holding h = new Holding();
        OffsetDateTime original = OffsetDateTime.now().minusDays(1);
        h.setCreatedAt(original);
        h.setUpdatedAt(original);

        Thread.sleep(2);
        invokePreUpdate(h);

        assertThat(h.getCreatedAt()).isEqualTo(original);
        assertThat(h.getUpdatedAt()).isAfter(original);
    }

    private static void invokePrePersist(Holding h) throws Exception {
        var m = Holding.class.getDeclaredMethod("prePersist");
        m.setAccessible(true);
        m.invoke(h);
    }

    private static void invokePreUpdate(Holding h) throws Exception {
        var m = Holding.class.getDeclaredMethod("preUpdate");
        m.setAccessible(true);
        m.invoke(h);
    }
}
