package com.ebee.auth.event;

import com.ebee.user.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PasswordChangedEvent extends ApplicationEvent {

    private final User user;

    public PasswordChangedEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
