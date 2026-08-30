insert into billing_plans (code, name, amount_cents, currency, trial_days, max_units, active)
values ('standard', 'Standard', 9900, 'BRL', 14, 10, true)
on conflict (code) do nothing;
