package hk.jiakan.tv.diagnostics;

import java.util.ArrayDeque;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded, memory-only diagnostics. Callers pass identifiers and codes, never stream URLs. */
public final class EventLog {
    private final ArrayDeque<String> events=new ArrayDeque<>();
    public synchronized void add(String event) {
        if(events.size()>=100) events.removeFirst();
        events.addLast(new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())+" "+event);
    }
    public synchronized String snapshot() { return String.join("\n",events); }
}
