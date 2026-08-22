//
//  SignUpView.swift
//  Fleet Management Test
//

import SwiftUI

struct SignUpView: View {

    @StateObject private var viewModel: SignUpViewModel
    @Environment(\.dismiss) private var dismiss

    init(session: SessionController) {
        _viewModel = StateObject(wrappedValue: SignUpViewModel(session: session))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    if let error = viewModel.formError {
                        Banner(text: error, kind: .error)
                    }

                    rolePicker
                    accountFields
                    passwordFields

                    // Only a driver has a licence, so the block is absent
                    // entirely rather than shown disabled.
                    if viewModel.requiresLicence {
                        licenceFields
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    createButton
                }
                .padding(24)
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity)
            }
            .background(Color(.systemGroupedBackground))
            .scrollDismissesKeyboard(.interactively)
            .animation(.easeInOut(duration: 0.2), value: viewModel.requiresLicence)
            .navigationTitle("Create account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    // MARK: - Sections

    private var rolePicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Role")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.secondary)
            Picker("Role", selection: $viewModel.role) {
                Text(UserRole.fleetManager.displayName).tag(UserRole.fleetManager)
                Text(UserRole.driver.displayName).tag(UserRole.driver)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("signup.role")
        }
    }

    private var accountFields: some View {
        VStack(spacing: 16) {
            LabeledField(title: "Full name", error: viewModel.fullNameError) {
                TextField("Priya Nair", text: $viewModel.fullName)
                    .accessibilityIdentifier("signup.fullName")
                    .textContentType(.name)
                    .autocorrectionDisabled()
            }

            LabeledField(title: "Email", error: viewModel.emailError) {
                TextField("ops@fleet.in", text: $viewModel.email)
                    .accessibilityIdentifier("signup.email")
                    .keyboardType(.emailAddress)
                    .textContentType(.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            LabeledField(
                title: viewModel.requiresLicence ? "Phone number" : "Phone number (optional)",
                error: viewModel.phoneError
            ) {
                TextField("+91 98765 43210", text: $viewModel.phoneNumber)
                    .accessibilityIdentifier("signup.phone")
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
            }
        }
    }

    private var passwordFields: some View {
        VStack(spacing: 16) {
            LabeledField(title: "Password", error: viewModel.passwordError) {
                HStack {
                    Group {
                        if viewModel.isPasswordVisible {
                            TextField("At least 8 characters", text: $viewModel.password)
                                .accessibilityIdentifier("signup.password.visible")
                        } else {
                            SecureField("At least 8 characters", text: $viewModel.password)
                                .accessibilityIdentifier("signup.password")
                        }
                    }
                    .textContentType(.newPassword)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

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

            LabeledField(title: "Confirm password", error: viewModel.confirmPasswordError) {
                SecureField("Type it again", text: $viewModel.confirmPassword)
                    .accessibilityIdentifier("signup.confirmPassword")
                    .textContentType(.newPassword)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        }
    }

    private var licenceFields: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Licence")
                .font(.headline)

            LabeledField(title: "Licence number", error: viewModel.licenseNumberError) {
                TextField("KA0120260005555", text: $viewModel.licenseNumber)
                    .accessibilityIdentifier("signup.licenseNumber")
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Licence expiry")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.secondary)
                DatePicker(
                    "Licence expiry",
                    selection: $viewModel.licenseExpiry,
                    displayedComponents: .date
                )
                .labelsHidden()
                .accessibilityIdentifier("signup.licenseExpiry")

                if let error = viewModel.licenseExpiryError {
                    Text(error)
                        .accessibilityIdentifier("field.error")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    private var createButton: some View {
        Button {
            viewModel.submit()
        } label: {
            ZStack {
                Text("Create account").opacity(viewModel.isSubmitting ? 0 : 1)
                if viewModel.isSubmitting {
                    ProgressView().tint(.white)
                }
            }
            .font(.headline)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
        }
        .buttonStyle(.borderedProminent)
        .accessibilityIdentifier("signup.submit")
        .disabled(!viewModel.canSubmit)
    }
}

#Preview {
    let environment = AppEnvironment(tokenStore: InMemoryTokenStore())
    return SignUpView(session: environment.session)
}
