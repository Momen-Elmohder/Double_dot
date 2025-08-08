# Double Dot Squash Academy App

A comprehensive Android app for managing squash academy trainees, designed for Head Coaches, Admins, and Coaches.

## Features

- **Authentication System**: Secure sign-in with Firebase Authentication
- **Role-based Access**: Different interfaces for Head Coach, Admin, and Coach
- **Firebase Integration**: Real-time data synchronization across devices
- **Modern UI**: Beautiful Material Design interface

## Current Features

### Sign-In Page
- Email and password authentication
- Role selection (Head Coach, Admin, Coach)
- Input validation
- Loading states
- Error handling

### Dashboard
- Welcome screen with user information
- Role display
- Sign out functionality

## Next Steps

The following features will be implemented next:
1. **Trainee Management**: Add, edit, remove trainee data
2. **Dashboard for each role**: Different interfaces based on user role
3. **Data synchronization**: Real-time updates across devices
4. **Advanced features**: Progress tracking, scheduling, etc.

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/double_dot_demo/
│   │   ├── MainActivity.kt (Sign-in screen)
│   │   ├── DashboardActivity.kt (Dashboard)
│   │   └── viewmodel/
│   │       └── SignInViewModel.kt (Authentication logic)
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml (Sign-in UI)
│       │   └── activity_dashboard.xml (Dashboard UI)
│       ├── drawable/ (Icons and graphics)
│       └── values/
│           └── colors.xml (App colors)
```

## Dependencies

- Firebase Authentication
- Firebase Firestore
- Material Design Components
- Android Architecture Components (ViewModel, LiveData)
- View Binding

## Support

For any issues or questions, please refer to the Firebase documentation or contact the development team. 
