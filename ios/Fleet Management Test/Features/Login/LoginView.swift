//
//  LoginView.swift
//  Fleet Management Test
//

import SwiftUI

struct LoginView: View {

    @StateObject private var viewModel: LoginViewModel
    @EnvironmentObject private var session: SessionController
    @FocusState private var focus: Field?
    @State private var isShowingSignUp = false

    private enum Field { case email, password }

    init(session: SessionController) {
        _viewModel = StateObject(wrappedValue: LoginViewModel(session: session))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header

                if let notice = session.expiryNotice {
                    Banner(text: notice, kind: .info)
                }
                if let error = viewModel.formError {
                    Banner(text: error, kind: .error)
                }

                VStack(spacing: 16) {
                    emailField
                    passwordField
                }

                signInButton
                createAccountButton
            }
            .padding(24)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .scrollDismissesKeyboard(.interactively)
        .sheet(isPresented: $isShowingSignUp) {
            SignUpView(session: session)
        }
    }

    // MARK: - Pieces

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: "truck.box.fill")
                .font(.largeTitle)
                .foregroundStyle(.tint)
                .padding(.bottom, 4)
            Text("Fleet Management")
                .font(.largeTitle.bold())
            Text("Sign in to see your vehicles and trips.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(.top, 32)
    }

    private var emailField: some View {
        LabeledField(title: "Email", error: viewModel.emailError) {
            TextField("ops@fleet.in", text: $viewModel.email)
                .accessibilityIdentifier("login.email")
                .keyboardType(.emailAddress)
                .textContentType(.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.next)
                .focused($focus, equals: .email)
                .onSubmit { focus = .password }
        }
    }

    private var passwordField: some View {
        LabeledField(title: "Password", error: viewModel.passwordError) {
            HStack {
                Group {
                    if viewModel.isPasswordVisible {
                        TextField("Your password", text: $viewModel.password)
                            .accessibilityIdentifier("login.password.visible")
                    } else {
                        SecureField("Your password", text: $viewModel.password)
                            .accessibilityIdentifier("login.password")
                    }
                }
                .textContentType(.password)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.go)
                .focused($focus, equals: .password)
                .onSubmit(submit)

                Button {
                    viewModel.isPasswordVisible.toggle()
                } label: {
                    Image(systemName: viewModel.isPasswordVisible ? "eye.slash" : "eye")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(
                    viewModel.isPasswordVisible ? "Hide password" : "Show password"
                )
            }
        }
    }

    private var signInButton: some View {
        Button(action: submit) {
            ZStack {
                // Kept in the layout so the button does not resize mid-request.
                Text("Sign in").opacity(viewModel.isSubmitting ? 0 : 1)
                if viewModel.isSubmitting {
                    ProgressView().tint(.white)
                }
            }
            .font(.headline)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
        }
        .buttonStyle(.borderedProminent)
        .accessibilityIdentifier("login.submit")
        .disabled(!viewModel.canSubmit)
        .accessibilityLabel(viewModel.isSubmitting ? "Signing in" : "Sign in")
    }

    private var createAccountButton: some View {
        HStack(spacing: 4) {
            Text("No account yet?")
                .foregroundStyle(.secondary)
            Button("Create one") {
                focus = nil
                isShowingSignUp = true
            }
            .accessibilityIdentifier("login.createAccount")
        }
        .font(.subheadline)
        .frame(maxWidth: .infinity)
    }

    private func submit() {
        focus = nil
        viewModel.submit()
    }
}

// MARK: - Small shared views

/// A titled input with an error message reserved beneath it.
struct LabeledField<Content: View>: View {
    let title: String
    let error: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.footnote.weight(.medium))
                .foregroundStyle(.secondary)

            content
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color(.secondarySystemGroupedBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(error == nil ? Color.clear : Color.red, lineWidth: 1)
                )

            if let error {
                Text(error)
                    .accessibilityIdentifier("field.error")
                    .font(.caption)
                    .foregroundStyle(.red)
                    .accessibilityLabel("\(title) error: \(error)")
            }
        }
    }
}

struct Banner: View {
    enum Kind { case error, info }

    let text: String
    let kind: Kind

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: kind == .error ? "exclamationmark.triangle.fill" : "info.circle.fill")
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .font(.subheadline)
        .foregroundStyle(kind == .error ? Color.red : Color.accentColor)
        .padding(12)
        .background(
            (kind == .error ? Color.red : Color.accentColor).opacity(0.1),
            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(kind == .error ? "banner.error" : "banner.info")
    }
}

#Preview {
    let environment = AppEnvironment(tokenStore: InMemoryTokenStore())
    return LoginView(session: environment.session)
        .environmentObject(environment.session)
}
