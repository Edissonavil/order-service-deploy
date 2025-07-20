/* src/main/resources/db/migration/V1__create_table_order_fact.sql */


create table order_fact (
    id            bigserial primary key,
    order_id      bigint      not null,
    collaborator  bigint      not null,
    total_usd     numeric(14,2) not null,
    created_at    timestamp   not null default now()
);
create index if not exists idx_order_fact_date on order_fact (created_at);
create index if not exists idx_order_fact_collab on order_fact (collaborator);
