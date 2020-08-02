/*
 * The MIT License
 *
 * Copyright (c) 2020, Nikolas Falco
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.plugin.android.emulator.sdk.cli;

import java.io.IOException;

import hudson.EnvVars;
import hudson.util.ArgumentListBuilder;

public class CLICommand<R> {

    public interface OutputParser<R> {
        R parse(String input) throws IOException;
    }

    private final ArgumentListBuilder arguments;
    private final EnvVars env;
    private OutputParser<R> parser;

    CLICommand(ArgumentListBuilder arguments, EnvVars env) {
        this(arguments, env, null);
    }

    CLICommand(ArgumentListBuilder arguments, EnvVars env, OutputParser<R> parser) {
        this.arguments = arguments;
        this.env = env;
        this.parser = parser;
    }

    public ArgumentListBuilder arguments() {
        return arguments;
    }

    public String getExecutable() {
        return arguments.toList().get(0);
    }

    public EnvVars env() {
        return env;
    }

    public R parse(String output) throws IOException {
        if (parser == null) {
            throw new IllegalStateException("This CLI does have an output parser");
        }

        return parser.parse(output);
    }
}