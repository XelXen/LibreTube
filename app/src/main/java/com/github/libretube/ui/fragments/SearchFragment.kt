package com.github.libretube.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.R
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.databinding.FragmentSearchBinding
import com.github.libretube.db.DatabaseHolder.Companion.Database
import com.github.libretube.extensions.BaseFragment
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.awaitQuery
import com.github.libretube.models.SearchViewModel
import com.github.libretube.ui.activities.MainActivity
import com.github.libretube.ui.adapters.SearchHistoryAdapter
import com.github.libretube.ui.adapters.SearchSuggestionsAdapter
import retrofit2.HttpException
import java.io.IOException

class SearchFragment : BaseFragment() {
    private lateinit var binding: FragmentSearchBinding
    private val viewModel: SearchViewModel by activityViewModels()

    private var query: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        query = arguments?.getString("query")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.suggestionsRecycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            reverseLayout = true
            stackFromEnd = true
        }

        // waiting for the query to change
        viewModel.searchQuery.observe(viewLifecycleOwner) {
            showData(it)
        }
    }

    private fun showData(query: String?) {
        // fetch the search or history
        binding.historyEmpty.visibility = View.GONE
        binding.suggestionsRecycler.visibility = View.VISIBLE
        if (query == null || query == "") {
            showHistory()
        } else {
            fetchSuggestions(query)
        }
    }

    private fun fetchSuggestions(query: String) {
        lifecycleScope.launchWhenCreated {
            val response = try {
                RetrofitInstance.api.getSuggestions(query)
            } catch (e: IOException) {
                println(e)
                Log.e(TAG(), "IOException, you might not have internet connection")
                return@launchWhenCreated
            } catch (e: HttpException) {
                Log.e(TAG(), "HttpException, unexpected response")
                return@launchWhenCreated
            }
            // only load the suggestions if the input field didn't get cleared yet
            val suggestionsAdapter =
                SearchSuggestionsAdapter(
                    response,
                    (activity as MainActivity).searchView
                )
            runOnUiThread {
                if (viewModel.searchQuery.value != "") {
                    binding.suggestionsRecycler.adapter = suggestionsAdapter
                }
            }
        }
    }

    private fun showHistory() {
        val historyList = awaitQuery {
            Database.searchHistoryDao().getAll().map { it.query }
        }
        if (historyList.isNotEmpty()) {
            binding.suggestionsRecycler.adapter =
                SearchHistoryAdapter(
                    historyList,
                    (activity as MainActivity).searchView
                )
        } else {
            binding.suggestionsRecycler.visibility = View.GONE
            binding.historyEmpty.visibility = View.VISIBLE
        }
    }

    override fun onStop() {
        if (findNavController().currentDestination?.id != R.id.searchResultFragment) {
            // remove the search focus
            (activity as MainActivity)
                .binding.toolbar.menu
                .findItem(R.id.action_search).collapseActionView()
        }
        super.onStop()
    }
}