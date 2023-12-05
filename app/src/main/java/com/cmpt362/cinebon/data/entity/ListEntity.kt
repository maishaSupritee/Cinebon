package com.cmpt362.cinebon.data.entity

import com.cmpt362.cinebon.data.api.response.Movie
import com.cmpt362.cinebon.data.objects.User
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Exclude

class ListEntity {
    @set:Exclude @get:Exclude
    var listId: String = ""

    lateinit var owner: DocumentReference
    lateinit var name: String
    var movies = mutableListOf<Int>()
}

data class ResolvedListEntity (
    val listId: String,
    val owner: User,
    val name: String,
    val movies: MutableList<Movie>,
    val isSelf: Boolean
)