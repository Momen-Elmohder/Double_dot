package com.example.double_dot_demo.models

import com.google.firebase.Timestamp

data class WaitingListPlayer(
    var id: String = "",
    var name: String = "",
    var age: Int = 0,
    var phoneNumber: String = "",
    var branch: String = "",
    var addedBy: String = "",
    var addedByName: String = "",
    var createdAt: com.google.firebase.Timestamp? = null,
    var status: String = "waiting" // waiting, contacted, enrolled, rejected
) {
    // Required empty constructor for Firestore
    constructor() : this("", "", 0, "", "", "", "", null, "waiting")
}
