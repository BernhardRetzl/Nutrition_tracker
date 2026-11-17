CREATE TABLE `logs` (
	`owner` text PRIMARY KEY NOT NULL,
	`revision` integer DEFAULT 0 NOT NULL,
	`snapshot` text DEFAULT '{"recipes":{},"foods":{},"entries":{}}' NOT NULL
);
