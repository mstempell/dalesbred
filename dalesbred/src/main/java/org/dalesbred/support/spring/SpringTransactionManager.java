/*
 * Copyright (c) 2015 Evident Solutions Oy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.dalesbred.support.spring;

import org.dalesbred.*;
import org.dalesbred.connection.ConnectionProvider;
import org.dalesbred.dialects.Dialect;
import org.dalesbred.internal.utils.Require;
import org.dalesbred.tx.TransactionManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link ConnectionProvider} which integrates with Spring's transaction management.
 * <p></p>
 * Usually application code should not need this class, but use {@link DalesbredConfigurationSupport} to
 * integrate with Spring.
 */
public final class SpringTransactionManager implements TransactionManager {

    @NotNull
    private final DataSource dataSource;

    @NotNull
    private final PlatformTransactionManager platformTransactionManager;

    @NotNull
    private Isolation defaultIsolation = Isolation.DEFAULT;

    @NotNull
    private Propagation defaultPropagation = Propagation.DEFAULT;

    /**
     * Constructs new SpringTransactionManager to use.
     */
    public SpringTransactionManager(@NotNull DataSource dataSource, @NotNull PlatformTransactionManager platformTransactionManager) {
        this.dataSource = Require.requireNonNull(dataSource);
        this.platformTransactionManager = Require.requireNonNull(platformTransactionManager);
    }

    @Override
    public <T> T withCurrentTransaction(@NotNull TransactionCallback<T> callback, @NotNull Dialect dialect) {
        return execute(callback, dialect, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_MANDATORY));
    }

    @Override
    public <T> T withTransaction(@NotNull TransactionSettings settings, @NotNull TransactionCallback<T> callback, @NotNull Dialect dialect) {
        return execute(callback, dialect, settingsToSpringDefinition(settings));
    }

    private <T> T execute(@NotNull TransactionCallback<T> callback, @NotNull Dialect dialect, @NotNull DefaultTransactionDefinition df) {
        TransactionTemplate tt = new TransactionTemplate(platformTransactionManager, df);
        return tt.execute(status -> {
            try {
                Connection connection = DataSourceUtils.getConnection(dataSource);
                try {
                    return callback.execute(new SpringTransactionContext(status, connection));
                } finally {
                    DataSourceUtils.releaseConnection(connection, dataSource);
                }
            } catch (SQLException e) {
                throw dialect.convertException(e);
            }
        });
    }

    @Override
    public boolean hasActiveTransaction() {
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        return conHolder != null && (conHolder.getConnectionHandle() != null || conHolder.isSynchronizedWithTransaction());
    }

    @NotNull
    @Override
    public Isolation getDefaultIsolation() {
        return defaultIsolation;
    }

    @Override
    public void setDefaultIsolation(@NotNull Isolation isolation) {
        this.defaultIsolation = Require.requireNonNull(isolation);
    }

    @NotNull
    @Override
    public Propagation getDefaultPropagation() {
        return defaultPropagation;
    }

    @Override
    public void setDefaultPropagation(@NotNull Propagation propagation) {
        defaultPropagation = Require.requireNonNull(propagation);
    }

    static int springIsolationCode(@NotNull Isolation isolation) {
        if (isolation == Isolation.DEFAULT)
            return TransactionDefinition.ISOLATION_DEFAULT;
        else
            return isolation.getJdbcLevel();
    }

    static int springPropagationCode(@NotNull Propagation propagation) {
        switch (propagation) {
            case DEFAULT:
            case REQUIRED:
                return TransactionDefinition.PROPAGATION_REQUIRED;
            case MANDATORY:
                return TransactionDefinition.PROPAGATION_MANDATORY;
            case NESTED:
                return TransactionDefinition.PROPAGATION_NESTED;
            case REQUIRES_NEW:
                return TransactionDefinition.PROPAGATION_REQUIRES_NEW;
        }
        throw new IllegalArgumentException("unknown propagation: " + propagation);
    }

    @NotNull
    private DefaultTransactionDefinition settingsToSpringDefinition(@NotNull TransactionSettings settings) {
        if (settings.getRetries() != 0)
            throw new DatabaseException("retries are not supported with Spring managed transactions");

        DefaultTransactionDefinition df = new DefaultTransactionDefinition();
        df.setIsolationLevel(springIsolationCode(settings.getIsolation().normalize(defaultIsolation)));
        df.setPropagationBehavior(springPropagationCode(settings.getPropagation().normalize(defaultPropagation)));
        return df;
    }
}
