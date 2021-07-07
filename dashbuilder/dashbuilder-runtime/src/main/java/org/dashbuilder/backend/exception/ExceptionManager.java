package org.dashbuilder.backend.exception;

import org.jboss.errai.config.rebind.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExceptionManager {

    private static Logger log = LoggerFactory.getLogger(ExceptionManager.class);

    /**
     * <p>Return a <code>@Portable RuntimeException</code> that can be captured by client side widgets.</p>
     *
     * @param e The exception that caused the error.
     * @return The portable exception to send to the client side.
     */
    public RuntimeException handleException(final Exception e) {
        log.error(e.getMessage(), e);
        if (e instanceof RuntimeException && EnvUtil.isPortableType(e.getClass()) ) {
            return (RuntimeException) e;
        }
        return new GenericPortableException( e.getMessage(), e );
    }
}
