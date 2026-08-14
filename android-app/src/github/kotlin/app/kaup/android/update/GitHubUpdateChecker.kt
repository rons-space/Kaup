package app.kaup.android.update

import app.kaup.shared.domain.update.UpdateChecker
import app.kaup.shared.models.update.UpdateResult
import javax.inject.Inject

/**
 * Checks GitHub Releases for a newer build. Sideload channel only.
 *
 * ADR-014 places this in `:core-network`, but that cannot satisfy the ADR's
 * own constraint that `kmp-app-updater` is strictly absent from the
 * `playstore` variant: `:core-network` is compiled into every flavor, so the
 * library would ship everywhere. Living in the `github` source set is the only
 * arrangement that keeps the Play Store build free of a self-update path,
 * which is a store policy requirement, not a preference. See #236.
 */
class GitHubUpdateChecker @Inject constructor() : UpdateChecker {

    override suspend fun checkForUpdate(): UpdateResult {
        // TODO(#231): drive kmp-app-updater against the releases API and map
        // its result. The dependency is declared githubImplementation-only, so
        // wiring it here cannot leak into the other flavors.
        return UpdateResult.UpToDate
    }
}
