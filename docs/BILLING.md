# Billing setup and monetization

## Model

| | Free | Pro |
|---|---|---|
| Foundations program, five free standalone sessions | ✓ | ✓ |
| Equipment-aware generation, exclusions, replacements, "make easier" | ✓ | ✓ |
| Adaptive progression | First **3** sessions (a sample) | ✓ |
| Reductions when a session was too hard | ✓ always | ✓ |
| History, Progress basics, export and import | ✓ always | ✓ |
| Dumbbell Strength and Steady Habit programs, Pro sessions | – | ✓ |
| Multiple equipment profiles, advanced customization and progress analysis | – | ✓ |

- **What counts toward the sample.** A session counts only when at least 50% of its main sets were
  done. The count is never reduced by importing an older backup: the higher count wins.
- **Upgrade offer.** The main offer appears after the sample is used. It is never shown during a
  workout.
- **Proposed prices.** These come from the brief: **US$4.99 a month** and **US$29.99 a year**. They
  are configured in Play Console, not in code. The app always shows the price and terms that
  Google Play returns for the person's country.
- **Placeholder prices.** These appear only in development builds when store prices are
  unavailable. They are labelled as placeholders (`DevelopmentBilling.PlaceholderOffers`).

**Paywall honesty.** The paywall must:

- show the price, the billing period and that it renews automatically;
- explain what is free;
- include Restore purchases and Manage subscription (which opens Google Play subscriptions);
- not use countdowns, fake discounts or pre-selected trials.

**Entitlement changes never interrupt a workout in progress.** They only affect what can be started
next. History is never locked.

## Google Play Console setup (external, required)

1. Create the app in Play Console with the final application ID.
2. Upload a signed build containing the Play Billing library to a testing track. Internal testing
   is enough to create products.
3. **Monetize › Subscriptions › Create subscription**, with product ID **`forma_pro`**. This must
   match `PlayBillingGateway.PRODUCT_ID`.
4. Add two auto-renewing base plans:
   - **`monthly`**, billed every 1 month, US$4.99 with local prices generated;
   - **`annual`**, billed every 1 year, US$29.99.

   These IDs must match `BASE_PLAN_MONTHLY` and `BASE_PLAN_ANNUAL`. Activate both.
5. Optional: add a free-trial offer. The paywall copy then needs a matching review: it must state
   exactly when billing starts.
6. **Setup › License testing.** Add tester Google accounts so test purchases are not charged.
7. Set up the payments profile, tax and the subscription cancellation survey as required.
8. Test the following with a license tester:
   - purchase;
   - cancel from Play;
   - expiry (test subscriptions renew every few minutes);
   - restore on a second device;
   - a pending purchase (the "slow test card");
   - offline start after purchase;
   - a refund from Play Console.

To test real billing in a **debug** build, set `buildConfigField("boolean", "USE_PLAY_BILLING", "true")`
for `debug` in `app/build.gradle.kts`. The debug application ID (`app.forma.debug`) must then also
exist in Play Console. Alternatively, test the release build on an internal track.

## What the client implementation does

The client is `app/src/main/kotlin/app/forma/android/billing/PlayBillingGateway.kt`. It uses
Play Billing Library 9.1 and is **unverified**: it has not been compiled or run.

- **Connection.** Connects with automatic service reconnection and pending purchases enabled.
- **Offers.** Queries product details for `forma_pro` and maps each base plan to a `PlanOffer`,
  using Play's formatted price.
- **Purchase.** Launches the billing flow from the resumed activity. The result is mapped as
  follows:

| Result | Handling |
|---|---|
| `USER_CANCELED` | Treated as cancelled. No error is shown. |
| `ITEM_ALREADY_OWNED` | Triggers a restore. |
| Network or service errors | Shown as a calm, retryable message. |

- **Pending purchases.** A pending purchase is shown as "pending" and gives **no** access until
  Play reports it as purchased.
- **Acknowledgement.** Purchased subscriptions are acknowledged. Unacknowledged purchases are
  refunded by Google after 3 days.
- **Restore and refresh.** `queryPurchasesAsync` runs on start, on every resume and on Restore. It
  returns only active subscriptions, so expiry and refunds remove access on the next check.
- **Offline.** The last confirmed entitlement is cached in DataStore. If Play is unreachable,
  confirmed Pro access is kept for **7 days** after the last confirmation. The cache file is
  excluded from Android device backup, so it cannot carry access to another device.
- **Development provider.** The release `BillingFactory` (in `app/src/release`) always constructs
  `PlayBillingGateway`. Only the debug factory can create `DevelopmentBilling`, so release builds
  never reference it and R8 removes it.

## Recommended before launch (not implemented)

- **Server-side verification.** Without a backend, a rooted device could fake entitlement locally.
  For a low-price subscription this risk is commonly accepted at launch, but a production setup
  should include:
  - a small backend that verifies purchase tokens with the Google Play Developer API
    (`purchases.subscriptionsv2.get`);
  - Real-Time Developer Notifications (Pub/Sub) for renewals, cancellations, grace periods,
    account holds and refunds;
  - linking tokens with `obfuscatedAccountId`.
- **Grace period and account hold.** Enable these in Play Console. Without a backend the client
  relies on `queryPurchasesAsync`:
  - **Grace period:** the subscription is still returned, so access continues.
  - **Account hold:** the subscription is not returned, so access pauses. The Membership screen
    shows Free with Restore and Manage subscription. A specific "fix your payment in Google Play"
    message needs the backend's subscription state and is deferred.
- **Sample-counter limits.** The free-sample counter is stored locally. Clearing app data resets
  it. This is an accepted limitation with no accounts; a backend would be needed to prevent it.
- **Price tests and regional pricing.** These are configured in Play Console, not in code.
