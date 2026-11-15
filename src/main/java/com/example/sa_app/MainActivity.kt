package com.example.sa_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sa_app.ui.theme.Sa_appTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val branches = listOf(
            Branch(1, "B001", "สาขาหลัก"),
            Branch(2, "B002", "สาขา 2")
        )

        setContent {
            Sa_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var loggedInUser by rememberSaveable { mutableStateOf<String?>(null) }
                    var screen by rememberSaveable { mutableStateOf("home") }
                    val employees = remember { mutableStateListOf<Employee>() }
                    val user = loggedInUser

                    if (user == null) {
                        LoginScreen(
                            onLoginSuccess = { loggedInUser = it },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (screen == "form") {
                        EmployeeFormScreen(
                            nextId = "E%03d".format(employees.size + 1),
                            branches = branches,
                            onSave = {
                                employees.add(it)
                                screen = "home"
                            },
                            onCancel = { screen = "home" },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .padding(16.dp)
                        ) {
                            Greeting(name = user)
                            Text("จำนวนพนักงาน: ${employees.size} คน")

                            Button(onClick = { screen = "form" }) {
                                Text("เพิ่มพนักงานใหม่")
                            }

                            employees.forEach {
                                Text("${it.employeeId}  ${it.firstName} ${it.lastName} - ${it.position}")
                            }
                            }

                            Button(onClick = { loggedInUser = null }) {
                                Text("ออกจากระบบ")
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Sa_appTheme {
        Greeting("Android")
    }
}