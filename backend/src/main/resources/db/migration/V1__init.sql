-- Technical table that proves the migration pipeline runs end to end.
-- Domain tables (jokes, tags, ...) arrive in epic 1, not here.
create table application_info (
    id   bigint generated always as identity primary key,
    name varchar(100) not null
);

insert into application_info (name) values ('bank-zartow');
