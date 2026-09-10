package io.casehub.platform.api.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResolvedTargetTest {

  @Test
  void user_factory_creates_user_kind() {
    var target = ResolvedTarget.user("user-1");
    assertThat(target.targetId()).isEqualTo("user-1");
    assertThat(target.kind()).isEqualTo(TargetKind.USER);
  }

  @Test
  void nonUser_factory_creates_non_user_kind() {
    var target = ResolvedTarget.nonUser("agent-1");
    assertThat(target.targetId()).isEqualTo("agent-1");
    assertThat(target.kind()).isEqualTo(TargetKind.NON_USER);
  }

  @Test
  void null_targetId_rejected() {
    assertThatThrownBy(() -> new ResolvedTarget(null, TargetKind.USER))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void null_kind_rejected() {
    assertThatThrownBy(() -> new ResolvedTarget("user-1", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void agent_and_system_target_types_exist() {
    assertThat(TargetType.AGENT).isNotNull();
    assertThat(TargetType.SYSTEM).isNotNull();
  }
}
