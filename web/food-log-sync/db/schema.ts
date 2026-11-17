import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core';
export const logs = sqliteTable('logs', {
  owner: text('owner').primaryKey(),
  revision: integer('revision').notNull().default(0),
  snapshot: text('snapshot').notNull().default('{"recipes":{},"foods":{},"entries":{}}'),
});
