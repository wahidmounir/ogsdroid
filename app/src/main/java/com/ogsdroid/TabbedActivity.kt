package com.ogsdroid

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import com.ogs.Challenge
import com.ogs.OGS
import com.ogs.OgsSocket
import com.ogs.SeekGraphConnection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.greenrobot.eventbus.EventBus
import org.json.JSONException
import java.util.*
import java.util.logging.Logger

class AccessTokenAvailableEvent
class UiConfigAvailableEvent

class TabbedActivity : AppCompatActivity() {
    internal var ogs: OGS? = null
    internal val gameList: ArrayList<Game> = ArrayList()
    internal val challengeList: ArrayList<Challenge> = ArrayList()
    lateinit internal var myGamesAdapter: MyGamesAdapter
    lateinit internal var challengeAdapter: ArrayAdapter<Challenge>
    internal var seek: SeekGraphConnection? = null
    internal val subscribers = CompositeDisposable()
    internal var nextPage = 1

    /**
     * Dispatch onPause() to fragments.
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        gameList.clear()
        challengeList.clear()
        challengeAdapter.notifyDataSetChanged()
        myGamesAdapter.notifyDataSetChanged()

        if (seek != null)
            seek!!.disconnect()

        println("XXXXXXXXXX disposing subscribers")
        subscribers.clear()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")

        ogs?.closeSocket()
        ogs = null
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")

        nextPage = 1

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        subscribers.add(Globals.getAccessToken(this)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { token ->
                            if (token == "") {
                                val intent = Intent(applicationContext, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                Globals.accessToken = token
                                EventBus.getDefault().post(AccessTokenAvailableEvent())
                                loadEverything()
                            }
                        },
                        { e ->
                            Log.e(TAG, "error while getting access token", e)
                            val intent = Intent(applicationContext, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                ))
    }

    fun loadEverything() {

        subscribers.add(Globals.ogsService.uiConfig()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { uiConfig -> Globals.uiConfig = uiConfig; OgsSocket.uiConfig = uiConfig },
                        { e ->
                            Log.e(TAG, "error while getting uiConfig", e)
                            val intent = Intent(applicationContext, LoginActivity::class.java)
                            startActivity(intent)
                            finish()
                        },
                        {
                            ogs = OGS(Globals.uiConfig!!)
                            ogs?.openSocket()

                            loadSeek()

                            println("NJJ posting that ui config is available!")
                            EventBus.getDefault().post(UiConfigAvailableEvent())
                        }
                ))
    }

    fun loadSeek() {
        seek = ogs?.openSeekGraph(SeekGraphConnection.SeekGraphConnectionCallbacks { events ->
            for (i in 0..events.length() - 1) {
                try {
                    val event = events.getJSONObject(i)
                    //Log.d(TAG, event.toString());
                    if (event.has("delete")) {
                        this@TabbedActivity.runOnUiThread {
                            try {
                                challengeAdapter.remove(Challenge(event.getInt("challenge_id")))
                                challengeAdapter.notifyDataSetChanged()
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        }
                    } else if (event.has("game_started"))
                    else
                    // new seek
                    {
                        val c = Challenge(event)
                        //Log.d(TAG, c.toString());

                        if (c.canAccept(Globals.uiConfig!!.user.ranking)) {
                            this@TabbedActivity.runOnUiThread {
                                challengeList.add(c)
                                Collections.sort(challengeList)
                                challengeAdapter.notifyDataSetChanged()
                            }
                        } else {
                            Log.d(TAG, "could not accept " + c)
                        }

                    }// game started notificaton
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    lateinit var mSectionsPagerAdapter: SectionsPagerAdapter
    lateinit var mViewPager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate savedInstanceState=${savedInstanceState}")
        super.onCreate(null)
        setContentView(R.layout.activity_tabbed)

        //val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        //StrictMode.setThreadPolicy(policy)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        challengeAdapter = ArrayAdapter(this,
                R.layout.activity_listview,
                challengeList)

        myGamesAdapter = MyGamesAdapter(this, gameList)

        val al = Alarm()
        al.setAlarm(this)

        val logger = Logger.getLogger(OgsSocket::class.java.name)
        println("NJJJ logger = ${logger}")
        println("NJJJ logger.level = ${logger.level}")
        println("NJJJ logger.name = ${logger.name}")

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById<ViewPager>(R.id.container)
        mViewPager.adapter = mSectionsPagerAdapter

        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        tabLayout.setupWithViewPager(mViewPager)


        //Intent intent = new Intent(this, NotificationService.class);
        //System.out.println("NJ creating service....");
        //startService(intent);
        //System.out.println("NJ done creating service....");
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_tabbed, menu)
        return true
    }

    private fun emailLogcat() {
        throw RuntimeException("gimme logs")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        Log.d(TAG, "id = " + id)

        if (id == R.id.action_settings) {
            val intent = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(intent)
        } else if (id == R.id.action_email_logs) {
            emailLogcat()
        }

        return super.onOptionsItemSelected(item)
    }

    inner class SectionsPagerAdapter internal constructor(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        val myGames = MyGamesFragment()
        val findAGame = FindAGameFragment()
        val automatch = AutomatchFragment()

        override fun getItem(position: Int): Fragment {
            when (position) {
                0 -> return myGames
                1 -> return findAGame
                else -> return automatch
            }
        }

        override fun getCount(): Int {
            return 3
        }

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return "My Games"
                1 -> return "Find a Game"
                2 -> return "Automatch"
            }
            return null
        }
    }

    companion object {
        private val TAG = "TabbedActivity"
    }
}
