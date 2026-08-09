package com.virtual_deadlock_detection.LockDetectionAgent.logging;

public class Logger {
    private final System.Logger delegate;

    private Logger(String name) {
        this.delegate = System.getLogger(name);
    }

    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz.getName());
    }

    public void debug(String msg, Object... args) {
        delegate.log(System.Logger.Level.DEBUG, format(msg, args));
    }

    public void info(String msg, Object... args) {
        delegate.log(System.Logger.Level.INFO, format(msg, args));
    }

    public void warn(String msg, Object... args) {
        delegate.log(System.Logger.Level.WARNING, format(msg, args));
    }

    public void error(String msg, Object... args) {
        delegate.log(System.Logger.Level.ERROR, format(msg, args));
    }

    private String format(String msg, Object... args) {
        if (args == null || args.length == 0) return msg;
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int cur = 0;
        while (cur < msg.length()) {
            int idx = msg.indexOf("{}", cur);
            if (idx == -1) {
                sb.append(msg.substring(cur));
                break;
            }
            sb.append(msg, cur, idx);
            if (argIdx < args.length) {
                sb.append(args[argIdx++]);
            } else {
                sb.append("{}");
            }
            cur = idx + 2;
        }
        return sb.toString();
    }
}
