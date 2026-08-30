package com.jpintodigital.billing.support;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.api.SubscriptionView;
import com.jpintodigital.billing.spi.SubscriptionListener;
import java.util.ArrayList;
import java.util.List;

public class RecordingSubscriptionListener implements SubscriptionListener {

    public record Change(SubscriptionStatus previous, SubscriptionStatus current) {
    }

    public final List<Change> changes = new ArrayList<>();

    @Override
    public void onChanged(SubscriptionView subscription, SubscriptionStatus previous) {
        changes.add(new Change(previous, subscription.status()));
    }

    public SubscriptionStatus lastStatus() {
        return changes.isEmpty() ? null : changes.get(changes.size() - 1).current();
    }
}
