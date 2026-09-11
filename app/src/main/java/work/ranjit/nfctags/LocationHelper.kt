package work.ranjit.nfctags

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object LocationHelper {
    private const val TAG = "LocationHelper"

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException fetching location for $provider", e)
            }
        }
        return bestLocation
    }

    suspend fun getCurrentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null

        val lastKnown = getLastKnownLocation(context)
        if (lastKnown != null && (System.currentTimeMillis() - lastKnown.time) < 120_000) {
            return@withContext lastKnown
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> LocationManager.PASSIVE_PROVIDER
                }
                val freshLocation = suspendCancellableCoroutine<Location?> { cont ->
                    val cancellationSignal = CancellationSignal()
                    cont.invokeOnCancellation { cancellationSignal.cancel() }
                    try {
                        locationManager.getCurrentLocation(
                            provider,
                            cancellationSignal,
                            context.mainExecutor
                        ) { loc ->
                            cont.resume(loc)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in getCurrentLocation callback", e)
                        cont.resume(lastKnown)
                    }
                }
                if (freshLocation != null) return@withContext freshLocation
            } catch (e: Exception) {
                Log.e(TAG, "Failed requesting fresh location", e)
            }
        }

        return@withContext lastKnown
    }

    fun formatMapsUrl(lat: Double, lon: Double): String = "https://maps.google.com/?q=$lat,$lon"

    fun processLocationVariables(text: String, location: Location?): String {
        if (location == null) {
            return text
                .replace("{{lat}}", "unknown")
                .replace("{{latitude}}", "unknown")
                .replace("{{lon}}", "unknown")
                .replace("{{longitude}}", "unknown")
                .replace("{{maps_url}}", "")
                .replace("{{location}}", "location unavailable")
        }

        val latStr = location.latitude.toString()
        val lonStr = location.longitude.toString()
        val mapsUrl = formatMapsUrl(location.latitude, location.longitude)
        val locStr = "$latStr,$lonStr"

        return text
            .replace("{{lat}}", latStr)
            .replace("{{latitude}}", latStr)
            .replace("{{lon}}", lonStr)
            .replace("{{longitude}}", lonStr)
            .replace("{{maps_url}}", mapsUrl)
            .replace("{{location}}", locStr)
    }
}
