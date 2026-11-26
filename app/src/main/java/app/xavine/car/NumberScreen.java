package app.xavine.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.InputCallback;
import androidx.car.app.model.Template;
import androidx.car.app.model.signin.InputSignInMethod;
import androidx.car.app.model.signin.SignInTemplate;

/** Asks for one number with the car's keyboard (available when the car is stopped). */
final class NumberScreen extends Screen {

    interface OnNumber {
        /** Return an error message to show, or null if the value was accepted. */
        String accept(double value);
    }

    private final String title;
    private final String instructions;
    private final String hint;
    private final boolean decimals;
    private final OnNumber onNumber;
    private String error;

    NumberScreen(@NonNull CarContext c, String title, String instructions, String hint,
                 boolean decimals, OnNumber onNumber) {
        super(c);
        this.title = title;
        this.instructions = instructions;
        this.hint = hint;
        this.decimals = decimals;
        this.onNumber = onNumber;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        InputSignInMethod.Builder input = new InputSignInMethod.Builder(new InputCallback() {
            @Override
            public void onInputSubmitted(@NonNull String text) {
                double v;
                try {
                    v = Double.parseDouble(text.trim().replace(",", "").replace("₹", ""));
                } catch (NumberFormatException e) {
                    error = "Please enter a number";
                    invalidate();
                    return;
                }
                String problem = onNumber.accept(v);
                if (problem != null) {
                    error = problem;
                    invalidate();
                }
            }
        })
                .setHint(hint)
                // The number pad on some cars has no decimal point, so litres use the full keyboard.
                .setKeyboardType(decimals ? InputSignInMethod.KEYBOARD_DEFAULT : InputSignInMethod.KEYBOARD_NUMBER)
                .setShowKeyboardByDefault(true);
        if (error != null) input.setErrorMessage(error);

        return new SignInTemplate.Builder(input.build())
                .setTitle(title)
                .setInstructions(instructions)
                .setHeaderAction(Action.BACK)
                .build();
    }
}
