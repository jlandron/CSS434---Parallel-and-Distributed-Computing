package UWAgent;
/*
 * $Id: CommandLineOptionAnalyzer.java,v 1.1.1.1 2008/08/01 14:57:15 uwagent Exp $
 *
 * Copyright (c) 2001- Daichi GOTO
 * Copyright (c) 2003- ONGS Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *   2. Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ONGS INC ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL ONGS INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of the ONGS Inc.
 */

//package jp.co.ongs.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Sample code.
 * <PRE>
 * <code>
 * CommandLineOptionAnalyzer analyser = 
 *     new CommandLineOptionAnalyzer(argv);
 *
 * Map map = analyser.getAnalyzedMap();
 *
 * if (map.containsKey("-l")) {
 *     System.err.println("");
 * }
 *
 * if (map.containsKey("-l")) {
 *     System.err.println(" " + map.get("-l") + "");
 * }
 * 
 * List list = map.get("arguments");
 * 
 * int size = list.size();
 * for (int count=0; count &lt size; count++) {
 *     System.err.println(list.get(count));
 * }
 * </code>
 * </PRE>
 *
 * @author Daichi GOTO (daichi@ongs.co.jp)
 * @version $Revision: 1.1.1.1 $
 */
public class CommandLineOptionAnalyzer
{

    /**
     * 
     */
    public CommandLineOptionAnalyzer()
    {
        // 
    }

    /**
     * 
     *
     * @param aCommandLine 
     */
    public CommandLineOptionAnalyzer(String aCommandLine)
    {
        this.setCommandLine(aCommandLine);
    }

    /**
     * 
     *
     * @param aCommandLine 
     */
    public CommandLineOptionAnalyzer(String[] aCommandLine)
    {
        this.setCommandLine(aCommandLine);
    }

    /**
     *
     * @param anyArguments 
     */
    public static void main(String[] anyArguments)
    {
        // 
        System.err.print("Arguments: ");
        int limit = anyArguments.length;
        for (int count=0; count<limit; count++) {
            System.err.print(anyArguments[count]);
            System.err.print(" ");
        }
        System.err.println("");

        CommandLineOptionAnalyzer analyzer =
            new CommandLineOptionAnalyzer(anyArguments);

        // 
        Map map = analyzer.getAnalyzedMap();
        Object[] keyArray = map.keySet().toArray();
        limit = keyArray.length;
        for (int count=0; count<limit; count++) {
            System.err.print(keyArray[count]);
            System.err.print("\t");
            System.err.print(map.get(keyArray[count]));
            System.err.println("");
        }
        map = null;
        keyArray = null;
    }


    /**
     *
     * @return Map
     */
    public Map getAnalyzedMap()
    {
        String option = this.getOptionPreString();
        String[] arguments = this.getCommandLine();
        Map<String, Object> map = new HashMap<String, Object>( );
        List<String> list = new ArrayList<String>();
        int count = 0;
        int limit = arguments.length;
        while (limit > count) {
            if (arguments[count].startsWith(option)) {
                // 
                if (limit - 1 == count) {
                    // 
                    // 
                    map.put(arguments[count], "");
                } else {
                    if (arguments[1 + count].startsWith(option)) {
                        // 
                        // 
                        map.put(arguments[count], "");
                    } else {
                        // 
                        // 
                        map.put(arguments[count], arguments[1 + count]);
                        ++count;
                    }
                }
            } else {
                // 
                list.add(arguments[count]);
            }
            ++count;
        }
        // 
        map.put("arguments", list);
        return (map);
    }

    /**
     * 
     *
     * @param aCommandLine 
     */
    public void setCommandLine(String aCommandLine)
    {
        StringTokenizer analyser = new StringTokenizer(aCommandLine);

        int limit = analyser.countTokens();
        String[] analyed = new String[limit];
        for (int count=0; count<limit; count++) {
            analyed[count] = analyser.nextToken();
        }
        analyser = null;
        this.setCommandLine(analyed);
    }

    /**
     * 
     *
     * @param aCommandLine 
     */
    public void setCommandLine(String[] aCommandLine)
    {
        this.commandLine = aCommandLine;
    }

    /**
     * 
     *
     * @return 
     */
    private String[] getCommandLine()
    {
        return (this.commandLine);
    }

    /**
     * 
     * 
     *
     * @return 
     * 
     */
    private String getOptionPreString()
    {
        if (null == this.optionPreString) {
            this.setOptionPreString("-");
        }
        return (this.optionPreString);
    }

    /**
     * 
     *
     * @param anOptionPreString 
     */
    private void setOptionPreString(String anOptionPreString)
    {
        this.optionPreString = anOptionPreString;
    }

    /**
     * 
     */
    private String[] commandLine = null;

    /**
     * 
     */
    private String optionPreString = null;

}
