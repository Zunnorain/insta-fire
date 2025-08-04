package com.example.instafire.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.extension_functions.setSafeOnClickListener
import com.example.instafire.R
import com.example.instafire.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentUser = auth.currentUser

        if (currentUser != null) {
            //User is logged in
            findNavController().navigate(R.id.action_loginFragment_to_postFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()

        binding.run {
            loginBtn.setSafeOnClickListener {
                login()
            }

        }

        return binding.root
    }


    private fun FragmentLoginBinding.login() {
        val email = emailET.text.toString()
        val pass = passwordET.text.toString()

        if (email.isNotEmpty() && pass.isNotEmpty()) {
            auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Login Success", Toast.LENGTH_SHORT).show()

                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(
                            R.id.loginFragment,
                            true
                        ).build()  // Clears everything up to loginFragment

                    findNavController().navigate(
                        R.id.action_loginFragment_to_postFragment,
                        null,
                        navOptions
                    )
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Toast.makeText(requireContext(), "No Field should be empty!", Toast.LENGTH_SHORT).show()
        }
    }

}