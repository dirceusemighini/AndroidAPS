/**
 * API for other modules to work with the database. Every write access to the DB must happen here,
 * through a class implementing * {@link info.nightscout.androidaps.database.transactions.Transaction}
 */
// TODO rename package transactions -> api?
package info.nightscout.androidaps.database.transactions;