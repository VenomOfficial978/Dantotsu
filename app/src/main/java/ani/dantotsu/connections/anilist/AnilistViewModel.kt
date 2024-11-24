package ani.dantotsu.connections.anilist

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.BuildConfig
import ani.dantotsu.R
import ani.dantotsu.connections.discord.Discord
import ani.dantotsu.connections.mal.MAL
import ani.dantotsu.media.Media
import ani.dantotsu.others.AppUpdater
import ani.dantotsu.profile.User
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.tryWithSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnilistViewModel : ViewModel() {

    private val animeContinue = MutableLiveData<List<Media>>() // Cleaner LiveData initialization
    private val animeFavorites = MutableLiveData<List<Media>>()
    private val mangaContinue = MutableLiveData<List<Media>>()
    private val mangaFavorites = MutableLiveData<List<Media>>()
    private val recommendation = MutableLiveData<List<Media>>()
    private val genres = MutableLiveData<Map<String, String>>()
    private val userStatus = MutableLiveData<List<User>>()

    val isDataLoaded = MutableLiveData(false) // Single source for data-loading status

    fun getAnimeContinue(): LiveData<List<Media>> = animeContinue
    fun getAnimeFavorites(): LiveData<List<Media>> = animeFavorites
    fun getMangaContinue(): LiveData<List<Media>> = mangaContinue
    fun getMangaFavorites(): LiveData<List<Media>> = mangaFavorites
    fun getRecommendations(): LiveData<List<Media>> = recommendation
    fun getGenres(): LiveData<Map<String, String>> = genres
    fun getUserStatus(): LiveData<List<User>> = userStatus

    suspend fun initHomePage() {
        // Load and post all required data for the homepage
        val homePageData = Anilist.query.initHomePage()
        with(homePageData) {
            animeContinue.postValue(this["currentAnime"])
            animeFavorites.postValue(this["favoriteAnime"])
            mangaContinue.postValue(this["currentManga"])
            mangaFavorites.postValue(this["favoriteManga"])
            recommendation.postValue(this["recommendations"])
        }
        isDataLoaded.postValue(true)
    }

    suspend fun loadGenresAndTags() {
        val fetchedGenres = Anilist.query.getGenresAndTags()
        genres.postValue(fetchedGenres)
    }

    suspend fun loadUserStatus() {
        val status = Anilist.query.getUserStatus()
        userStatus.postValue(status)
    }

    suspend fun loadAppData(context: FragmentActivity) {
        // Fetch tokens and check for updates
        Anilist.getSavedToken()
        MAL.getSavedToken()
        Discord.getSavedToken()

        if (!BuildConfig.FLAVOR.contains("fdroid") && PrefManager.getVal(PrefName.CheckUpdate)) {
            context.lifecycleScope.launch(Dispatchers.IO) {
                AppUpdater.check(context, false)
            }
        }

        // Fetch genres and tags
        loadGenresAndTags()
    }
}

fun interface PagedLoader {
    suspend fun loadNextPage(results: SearchResults): SearchResults
}

class SharedPagedLoader : PagedLoader {
    override suspend fun loadNextPage(results: SearchResults): SearchResults {
        return Anilist.query.search(
            results.type,
            results.page + 1,
            results.perPage,
            results.search,
            results.sort,
            results.genres,
            results.tags,
            results.status,
            results.source,
            results.format,
            results.countryOfOrigin,
            results.isAdult,
            results.onList,
            adultOnly = PrefManager.getVal(PrefName.AdultOnly)
        ) ?: results // Return existing results if the new fetch fails
    }
}
