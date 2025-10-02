package com.synaptix.capetowncoffees.data.repository

import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.domain.model.Review
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

//class ReviewRepository(
//    firestore: FirebaseFirestore
//) : BaseRepository<Review>(firestore) {
//    override val collection: CollectionReference
//        get() = firestore.collection("reviews")
//
//    override fun getType(): Class<Review> = Review::class.java
//}