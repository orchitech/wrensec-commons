/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017 ForgeRock AS.
 */
package org.forgerock.tokenhandler;

/**
 * An exception generated by a {@link TokenHandler} on extraction when the token is expired.
 */
public class ExpiredTokenException extends InvalidTokenException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the message
     */
    public ExpiredTokenException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause the cause
     */
    public ExpiredTokenException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public ExpiredTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
