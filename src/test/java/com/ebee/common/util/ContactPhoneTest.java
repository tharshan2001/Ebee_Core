package com.ebee.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactPhoneTest {

    @Test
    void localNumbersAreStoredTheSameWay_foreignOnesAsTyped() {
        assertThat(PhoneUtils.contactPhone("212345678")).isEqualTo("0212345678"); // spreadsheet dropped the 0
        assertThat(PhoneUtils.contactPhone("+94 21 234 5678")).isEqualTo("0212345678");
        assertThat(PhoneUtils.contactPhone("021-2345678")).isEqualTo("0212345678");
        assertThat(PhoneUtils.contactPhone("+44 20 7946 0000")).isEqualTo("+44 20 7946 0000");
        assertThat(PhoneUtils.contactPhone(" ")).isNull();
    }

    @Test
    void textIsRejected() {
        assertThatThrownBy(() -> PhoneUtils.contactPhone("call Sunil")).hasMessageContaining("isn't a phone number");
    }
}
