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

package org.dalesbred.integration.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import org.dalesbred.Database;
import org.jetbrains.annotations.NotNull;

import javax.inject.Singleton;

import static java.util.Objects.requireNonNull;
import static org.dalesbred.integration.guice.GuiceSupport.bindTransactionInterceptor;

/**
 * A Guice module for configuring the database using a {@link javax.sql.DataSource}.
 * Assumes that a DataSource has been bound to the context.
 */
public final class DataSourceDatabaseModule extends AbstractModule {

    @NotNull
    private final Key<Database> databaseKey;

    /**
     * Creates a module that creates a default database instance.
     */
    public DataSourceDatabaseModule() {
        this(Key.get(Database.class));
    }

    /**
     * Creates a module that creates a database instance with given key.
     */
    public DataSourceDatabaseModule(@NotNull Key<Database> databaseKey) {
        this.databaseKey = requireNonNull(databaseKey);
    }

    @Override
    protected void configure() {
        bind(databaseKey).toProvider(DatabaseProvider.class).in(Singleton.class);

        bindTransactionInterceptor(binder(), databaseKey);
    }
}
