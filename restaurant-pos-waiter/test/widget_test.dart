import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:restaurant_pos_waiter/features/waiter/presentation/pages/login_page.dart';

void main() {
  testWidgets('LoginPage renders header, fields, and login button', (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: LoginPage(),
      ),
    );

    expect(find.text('AnnaDhara'), findsWidgets);
    expect(find.text('Waiter Login'), findsOneWidget);
    expect(find.byType(TextFormField), findsNWidgets(2));
    expect(find.text('LOGIN'), findsOneWidget);
  });

  testWidgets('LoginPage shows validation errors when fields are empty', (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: LoginPage(),
      ),
    );

    await tester.tap(find.text('LOGIN'));
    await tester.pump();

    expect(find.text('Email is required'), findsOneWidget);
    expect(find.text('Password is required'), findsOneWidget);
  });

  testWidgets('LoginPage validates invalid email format', (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: LoginPage(),
      ),
    );

    await tester.enterText(find.byType(TextFormField).first, 'invalid-email');
    await tester.enterText(find.byType(TextFormField).last, 'password123');
    await tester.tap(find.text('LOGIN'));
    await tester.pump();

    expect(find.text('Please enter a valid email address'), findsOneWidget);
  });
}
